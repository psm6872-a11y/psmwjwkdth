const { onDocumentWritten } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");

admin.initializeApp();

const db = admin.firestore();

exports.onCalendarEventWritten = onDocumentWritten("rooms/{roomCode}/events/{eventId}", async (event) => {
    const roomCode = event.params.roomCode;
    const eventId = event.params.eventId;

    const beforeData = event.data.before ? event.data.before.data() : null;
    const afterData = event.data.after ? event.data.after.data() : null;

    let changeType = ""; // CREATE, UPDATE, DELETE
    let eventTitle = "";
    let dateStr = "";
    let timeStr = "";
    let nickname = "";
    let lastUpdatedBy = "";

    if (afterData && !beforeData) {
        changeType = "CREATE";
        eventTitle = afterData.title || "(제목 없음)";
        lastUpdatedBy = afterData.createdBy || afterData.lastUpdatedBy || "";

        const startMillis = afterData.startMillis;
        const isAllDay = afterData.isAllDay;
        const startDate = new Date(startMillis);
        const y = startDate.getFullYear();
        const m = String(startDate.getMonth() + 1).padStart(2, '0');
        const d = String(startDate.getDate()).padStart(2, '0');
        dateStr = `${y}-${m}-${d}`;

        if (!isAllDay) {
            const hh = String(startDate.getHours()).padStart(2, '0');
            const mm = String(startDate.getMinutes()).padStart(2, '0');
            timeStr = ` ${hh}:${mm}`;
        }
    } else if (afterData && beforeData) {
        changeType = "UPDATE";
        eventTitle = afterData.title || "(제목 없음)";
        lastUpdatedBy = afterData.lastUpdatedBy || afterData.createdBy || "";

        const startMillis = afterData.startMillis;
        const isAllDay = afterData.isAllDay;
        const startDate = new Date(startMillis);
        const y = startDate.getFullYear();
        const m = String(startDate.getMonth() + 1).padStart(2, '0');
        const d = String(startDate.getDate()).padStart(2, '0');
        dateStr = `${y}-${m}-${d}`;

        if (!isAllDay) {
            const hh = String(startDate.getHours()).padStart(2, '0');
            const mm = String(startDate.getMinutes()).padStart(2, '0');
            timeStr = ` ${hh}:${mm}`;
        }
    } else if (!afterData && beforeData) {
        changeType = "DELETE";
        eventTitle = beforeData.title || "(제목 없음)";
        lastUpdatedBy = beforeData.lastUpdatedBy || beforeData.createdBy || "";
    }

    if (!changeType) return null;

    if (lastUpdatedBy) {
        try {
            const memberDoc = await db.collection("rooms")
                .doc(roomCode)
                .collection("members")
                .doc(lastUpdatedBy)
                .get();
            if (memberDoc.exists) {
                nickname = memberDoc.data().nickname || "알 수 없는 멤버";
            } else {
                nickname = "공유 멤버";
            }
        } catch (err) {
            console.error("Error getting member nickname:", err);
            nickname = "공유 멤버";
        }
    } else {
        nickname = "공유 멤버";
    }

    let title = "";
    let body = "";

    if (changeType === "CREATE") {
        title = `${nickname}님이 새 일정을 추가했어요`;
        body = `📅 ${eventTitle} - ${dateStr}${timeStr}`;
    } else if (changeType === "UPDATE") {
        title = `${nickname}님이 일정을 수정했어요`;
        body = `✏️ ${eventTitle} - ${dateStr}${timeStr}`;
    } else if (changeType === "DELETE") {
        title = `${nickname}님이 일정을 삭제했어요`;
        body = `🗑️ ${eventTitle}`;
    }

    // Fetch target tokens
    let membersSnapshot;
    try {
        membersSnapshot = await db.collection("rooms")
            .doc(roomCode)
            .collection("members")
            .get();
    } catch (err) {
        console.error("Error getting room members:", err);
        return null;
    }

    const tokens = [];
    membersSnapshot.forEach(doc => {
        if (doc.id !== lastUpdatedBy) {
            const data = doc.data();
            if (data.fcmToken) {
                tokens.push(data.fcmToken);
            }
        }
    });

    if (tokens.length === 0) {
        console.log("No recipient tokens found.");
        return null;
    }

    const targetDateMillis = afterData ? afterData.startMillis : (beforeData ? beforeData.startMillis : Date.now());

    const message = {
        data: {
            title: title,
            body: body,
            click_action: "danallacalendar://view?dateMillis=" + targetDateMillis,
            dateMillis: String(targetDateMillis)
        },
        tokens: tokens
    };

    try {
        const response = await admin.messaging().sendEachForMulticast(message);
        console.log(`Successfully sent ${response.successCount} messages; ${response.failureCount} failed.`);
    } catch (error) {
        console.error("Error sending messages:", error);
    }
    return null;
});
