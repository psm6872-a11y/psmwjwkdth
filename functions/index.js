const { onDocumentWritten } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");

admin.initializeApp();

const db = admin.firestore();

function formatEventDateTime(startMillis, isAllDay) {
    if (!startMillis) return "";
    const date = new Date(startMillis);
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    if (isAllDay) {
        return `${m}/${d}`;
    } else {
        const hh = String(date.getHours()).padStart(2, '0');
        const mm = String(date.getMinutes()).padStart(2, '0');
        return `${m}/${d} ${hh}:${mm}`;
    }
}

exports.onCalendarEventWritten = onDocumentWritten("rooms/{roomCode}/events/{eventId}", async (event) => {
    const roomCode = event.params.roomCode;
    const eventId = event.params.eventId;

    const beforeData = event.data.before ? event.data.before.data() : null;
    const afterData = event.data.after ? event.data.after.data() : null;

    let changeType = "";
    let eventTitle = "";
    let nickname = "";
    let lastUpdatedBy = "";

    if (afterData && !beforeData) {
        changeType = "CREATE";
        eventTitle = afterData.title || "(제목 없음)";
        lastUpdatedBy = afterData.createdBy || afterData.lastUpdatedBy || "";
    } else if (afterData && beforeData) {
        changeType = "UPDATE";
        eventTitle = afterData.title || "(제목 없음)";
        lastUpdatedBy = afterData.lastUpdatedBy || afterData.createdBy || "";
    } else if (!afterData && beforeData) {
        changeType = "DELETE";
        eventTitle = beforeData.title || "(제목 없음)";
        lastUpdatedBy = beforeData.lastUpdatedBy || beforeData.createdBy || "";
    }

    if (!changeType) return null;

    // ✅ CREATE/UPDATE 시 30초 쿨다운 체크
    if (changeType === "CREATE" || changeType === "UPDATE") {
        const cooldownRef = db.collection("rooms")
            .doc(roomCode)
            .collection("fcm_cooldown")
            .doc(eventId);

        const now = Date.now();
        const cooldownDoc = await cooldownRef.get();

        if (cooldownDoc.exists) {
            const lastSentAt = cooldownDoc.data().lastSentAt || 0;
            if (now - lastSentAt < 30000) {
                console.log(`쿨다운 중 - 알림 스킵 (${Math.round((now - lastSentAt) / 1000)}초 경과)`);
                // 시각만 갱신
                await cooldownRef.set({ lastSentAt: now });
                return null;
            }
        }

        // 30초 지났거나 첫 수정 → 시각 저장 후 알림 전송
        await cooldownRef.set({ lastSentAt: now });
    }

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
        title = `${nickname}님이 일정 추가`;
        const formattedTime = formatEventDateTime(afterData.startMillis, afterData.isAllDay);
        body = `📅 ${eventTitle} - ${formattedTime}`;
    } else if (changeType === "UPDATE") {
        title = `${nickname}님이 일정 수정`;

        const beforeTitle = beforeData.title || "(제목 없음)";
        const afterTitle = afterData.title || "(제목 없음)";
        const titleChanged = beforeTitle !== afterTitle;

        const beforeFormatted = formatEventDateTime(beforeData.startMillis, beforeData.isAllDay);
        const afterFormatted = formatEventDateTime(afterData.startMillis, afterData.isAllDay);
        const timeOrDateChanged = beforeFormatted !== afterFormatted;

        const bodyParts = [];
        if (titleChanged) {
            bodyParts.push(`${beforeTitle} → ${afterTitle}`);
        }
        if (timeOrDateChanged) {
            bodyParts.push(`${beforeFormatted} → ${afterFormatted}`);
        }
        body = bodyParts.join("\n");
    } else if (changeType === "DELETE") {
        title = `${nickname}님이 일정 삭제`;
        body = `🗑️ ${eventTitle}`;
    }

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

    const messageData = {
        title: title,
        click_action: "danallacalendar://view?dateMillis=" + targetDateMillis,
        dateMillis: String(targetDateMillis)
    };
    if (body) {
        messageData.body = body;
    }

    const message = {
        data: messageData,
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
