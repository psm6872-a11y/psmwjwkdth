const { onDocumentWritten } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");

admin.initializeApp();

const db = admin.firestore();

function formatEventDateTime(startMillis, isAllDay) {
    if (!startMillis) return "";
    const kstMillis = startMillis + (9 * 60 * 60 * 1000);
    const date = new Date(kstMillis);
    const m = String(date.getUTCMonth() + 1).padStart(2, '0');
    const d = String(date.getUTCDate()).padStart(2, '0');
    if (isAllDay) {
        return `${m}/${d}`;
    } else {
        const hh = String(date.getUTCHours()).padStart(2, '0');
        const mm = String(date.getUTCMinutes()).padStart(2, '0');
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

    // 완료 버튼 변경 여부 확인
    const beforeIsCompleted = beforeData ? (beforeData.isCompleted || false) : false;
    const afterIsCompleted = afterData ? (afterData.isCompleted || false) : false;
    const isCompletedChanged = changeType === "UPDATE" && (beforeIsCompleted !== afterIsCompleted);

    // 견적서 신규 생성 및 연결 여부 확인
    const beforeEstimateId = beforeData ? (beforeData.linkedEstimateId || "") : "";
    const afterEstimateId = afterData ? (afterData.linkedEstimateId || "") : "";
    const estimateLinkedNow = changeType === "UPDATE" && !beforeEstimateId && afterEstimateId;

    // ✅ CREATE/UPDATE 시 30초 쿨다운 체크 (단, 완료 버튼 상태 변경 및 견적서 작성완료는 예외)
    if ((changeType === "CREATE" || changeType === "UPDATE") && !isCompletedChanged && !estimateLinkedNow) {
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

    if (changeType === "CREATE") {
        nickname = afterData.createdBy || afterData.updatedBy || "";
    } else if (changeType === "UPDATE") {
        nickname = afterData.updatedBy || afterData.createdBy || "";
    } else if (changeType === "DELETE") {
        nickname = beforeData.updatedBy || beforeData.createdBy || "";
    }

    if (!nickname || nickname === "공유 멤버" || nickname === "알 수 없는 멤버") {
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
    }

    let title = "";
    let body = "";

    if (changeType === "CREATE") {
        title = `${nickname}님이 일정을 추가했습니다.`;
        const formattedTime = formatEventDateTime(afterData.startMillis, afterData.isAllDay);
        body = `📅 ${eventTitle} - ${formattedTime}`;
    } else if (changeType === "UPDATE") {
        // ✅ isCompleted 변경 감지 (최우선 처리)
        if (isCompletedChanged) {
            if (!beforeIsCompleted && afterIsCompleted) {
                // false → true: 완료 버튼 누름
                title = `${nickname}님이 완료 버튼을 눌렀습니다.`;
                body = `✅ ${eventTitle}`;
            } else if (beforeIsCompleted && !afterIsCompleted) {
                // true → false: 완료 버튼 해제
                title = `${nickname}님이 완료 버튼을 해제했습니다.`;
                body = `↩️ ${eventTitle}`;
            }
        } else if (estimateLinkedNow) {
            // ✅ 견적서 신규 생성 및 연결 감지
            title = `${nickname}님이 견적서 작성완료`;
            body = `📄 ${eventTitle}`;
        } else {
            // isCompleted 변경 없음 & 견적서 연결 없음 → 기존 필드 변경 감지 로직
            title = `${nickname}님이 일정을 변경했습니다.`;

            const beforeTitle = beforeData.title || "(제목 없음)";
            const afterTitle = afterData.title || "(제목 없음)";
            const titleChanged = beforeTitle !== afterTitle;

            const beforeFormatted = formatEventDateTime(beforeData.startMillis, beforeData.isAllDay);
            const afterFormatted = formatEventDateTime(afterData.startMillis, afterData.isAllDay);
            const timeOrDateChanged = beforeFormatted !== afterFormatted;

            const beforeLocation = beforeData.location || "";
            const afterLocation = afterData.location || "";
            const beforeDepAddress = (beforeLocation.split("|||")[0] || "").trim();
            const afterDepAddress = (afterLocation.split("|||")[0] || "").trim();
            const beforeDepFirstWord = beforeDepAddress.split(/\s+/)[0] || "";
            const afterDepFirstWord = afterDepAddress.split(/\s+/)[0] || "";
            const departureChanged = beforeDepFirstWord !== afterDepFirstWord;

            const beforeNotes = beforeData.notes || "";
            const afterNotes = afterData.notes || "";
            const beforePhone = (beforeNotes.split("|||")[0] || "").trim();
            const afterPhone = (afterNotes.split("|||")[0] || "").trim();
            const phoneChanged = beforePhone !== afterPhone;

            const bodyParts = [];
            if (titleChanged) {
                bodyParts.push(`제목\n${beforeTitle} → ${afterTitle}`);
            }
            if (timeOrDateChanged) {
                bodyParts.push(`시간\n${beforeFormatted} → ${afterFormatted}`);
            }
            if (departureChanged && (beforeDepFirstWord || afterDepFirstWord)) {
                bodyParts.push(`주소\n${beforeDepFirstWord || "(없음)"} → ${afterDepFirstWord || "(없음)"}`);
            }
            if (phoneChanged && (beforePhone || afterPhone)) {
                bodyParts.push(`전화번호\n${beforePhone || "(없음)"} → ${afterPhone || "(없음)"}`);
            }

            if (bodyParts.length === 0) {
                console.log("No significant fields changed in UPDATE. Skipping notification.");
                return null;
            }

            body = bodyParts.join("\n\n");
        }
    } else if (changeType === "DELETE") {
        title = `${nickname}님이 일정을 삭제했습니다.`;
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
        dateMillis: String(targetDateMillis),
        syncId: eventId
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

exports.onDeadlineDateWritten = onDocumentWritten("rooms/{roomCode}/deadline_dates/{dateMillis}", async (event) => {
    const roomCode = event.params.roomCode;
    const dateMillisStr = event.params.dateMillis;

    const beforeData = event.data.before ? event.data.before.data() : null;
    const afterData = event.data.after ? event.data.after.data() : null;

    // 1. 이미 문서가 진짜 삭제된 상태(afterData가 null)라면 리트리거(double trigger) 방지를 위해 리턴
    if (!afterData) return null;

    const status = afterData.status || "";
    const isDeletion = status === "DELETED";

    // 2. 누가 액션을 취했는지 파악 (등록: createdBy, 해제: deletedBy)
    const actorUuid = isDeletion ? (afterData.deletedBy || "") : (afterData.createdBy || "");
    if (!actorUuid) return null;

    // 3. 날짜 형식 포맷 (MM/DD)
    const dateMillis = parseInt(dateMillisStr);
    if (isNaN(dateMillis)) return null;
    const kstMillis = dateMillis + (9 * 60 * 60 * 1000);
    const date = new Date(kstMillis);
    const m = String(date.getUTCMonth() + 1).padStart(2, '0');
    const d = String(date.getUTCDate()).padStart(2, '0');
    const formattedDate = `${m}/${d}`;

    // 4. 닉네임 구하기
    let nickname = "";
    try {
        const memberDoc = await db.collection("rooms")
            .doc(roomCode)
            .collection("members")
            .doc(actorUuid)
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

    // 5. 알림 제목 및 본문 구성
    let title = "";
    let body = "";
    if (isDeletion) {
        title = `${nickname}님이 마감도장 해제`;
        body = `${nickname}님이 ${formattedDate} 마감도장을 해제하였습니다.`;
    } else {
        title = `${nickname}님이 마감도장 등록`;
        body = `${nickname}님이 ${formattedDate} 마감도장을 찍었습니다.`;
    }

    // 6. 대상 토큰 구하기 (도장을 찍은 본인 제외)
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
        if (doc.id !== actorUuid) {
            const data = doc.data();
            if (data.fcmToken) {
                tokens.push(data.fcmToken);
            }
        }
    });

    if (tokens.length === 0) {
        console.log("No recipient tokens found.");
        // 해제 요청이었다면 알림은 안 보내더라도 문서는 결국 삭제해줘야 함!
        if (isDeletion) {
            try {
                await event.data.after.ref.delete();
            } catch (err) {
                console.error("Failed to delete document from Firestore in fallback:", err);
            }
        }
        return null;
    }

    const message = {
        data: {
            title: title,
            body: body,
            click_action: "danallacalendar://view?dateMillis=" + dateMillis,
            dateMillis: String(dateMillis)
        },
        tokens: tokens
    };

    try {
        const response = await admin.messaging().sendEachForMulticast(message);
        console.log(`Successfully sent ${response.successCount} messages; ${response.failureCount} failed.`);
    } catch (error) {
        console.error("Error sending messages:", error);
    }

    // 7. 해제(DELETED 상태)인 경우, 알림을 보내고 난 뒤에 문서를 진짜로 삭제
    if (isDeletion) {
        try {
            await event.data.after.ref.delete();
            console.log(`Successfully deleted deadline date document ${dateMillisStr} after sending notification.`);
        } catch (err) {
            console.error("Failed to delete document from Firestore:", err);
        }
    }

    return null;
});
