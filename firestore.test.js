const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-d8748cc4-61c4-44d9-bc42-d37f5af0c828";
const ADMIN_UID = "admin_rep_1";
const STUDENT_UID = "student_alice";
const TA_UID = "ta_bob";
const OUTSIDER_UID = "outsider_eve";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function nowTimestamp() {
  return new Date();
}

function baseUserProfile(uid, overrides = {}) {
  return {
    userId: uid,
    displayName: "أحمد محمد",
    email: `${uid}@eng.edu`,
    studentRecordNumber: "20261001",
    department: "هندسة الحاسبات",
    academicYear: 2,
    term: 1,
    role: "student",
    approvalStatus: "pending",
    batchId: "",
    assignedCourseIds: [],
    enrolledCourseIds: [],
    darkMode: false,
    notifyLectures: true,
    notifyAssignments: true,
    createdAt: nowTimestamp(),
    updatedAt: nowTimestamp(),
    ...overrides,
  };
}

async function seedAdminAndCourse() {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("admins").doc(ADMIN_UID).set({
      userId: ADMIN_UID,
      grantedBy: ADMIN_UID,
      createdAt: nowTimestamp(),
    });
    await db.collection("users").doc(ADMIN_UID).set(baseUserProfile(ADMIN_UID, {
      displayName: "ممثل الدفعة",
      role: "batch_rep",
      approvalStatus: "approved",
      batchId: "eng_2026",
    }));
    await db.collection("users").doc(STUDENT_UID).set(baseUserProfile(STUDENT_UID, {
      displayName: "سارة علي",
      role: "student",
      approvalStatus: "approved",
      batchId: "eng_2026",
    }));
    await db.collection("users").doc(TA_UID).set(baseUserProfile(TA_UID, {
      displayName: "م. طارق",
      role: "ta",
      approvalStatus: "approved",
      batchId: "eng_2026",
      assignedCourseIds: ["course_math"],
    }));
    await db.collection("courses").doc("course_math").set({
      title: "الرياضيات الهندسية",
      code: "MATH201",
      academicYear: 2,
      term: 1,
      instructorName: "د. حسن محمود",
      department: "هندسة",
      accentHex: "#1E3A8A",
      batchId: "eng_2026",
      createdBy: ADMIN_UID,
      createdAt: nowTimestamp(),
      updatedAt: nowTimestamp(),
    });
    await db.collection("courses").doc("course_phys").set({
      title: "الفيزياء",
      code: "PHYS101",
      academicYear: 1,
      term: 1,
      instructorName: "د. خالد",
      department: "هندسة",
      accentHex: "#0D9488",
      batchId: "eng_2026",
      createdBy: ADMIN_UID,
      createdAt: nowTimestamp(),
      updatedAt: nowTimestamp(),
    });
  });
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) await testEnv.cleanup();
});

beforeEach(async () => {
  if (testEnv) await testEnv.clearFirestore();
});

test("Unauthenticated user cannot read courses or users", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("courses").where("batchId", "==", "eng_2026").get());
  await assertFails(unauthDb.collection("users").doc(STUDENT_UID).get());
});

test("Student cannot self-escalate role to batch_rep on profile create", async () => {
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();
  await assertFails(
    outsiderDb.collection("users").doc(OUTSIDER_UID).set(baseUserProfile(OUTSIDER_UID, {
      role: "batch_rep",
      approvalStatus: "approved",
      batchId: "eng_2026",
    }))
  );
});

test("Student can join immediately with valid batch joinCode ENG-2026", async () => {
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();
  await assertSucceeds(
    outsiderDb.collection("users").doc(OUTSIDER_UID).set(baseUserProfile(OUTSIDER_UID, {
      role: "student",
      approvalStatus: "approved",
      batchId: "eng_2026",
      joinCode: "ENG-2026",
    }))
  );
});

test("Student cannot read another student's PII user profile", async () => {
  await seedAdminAndCourse();
  const studentDb = testEnv.authenticatedContext(STUDENT_UID).firestore();
  await assertFails(studentDb.collection("users").doc(TA_UID).get());
  await assertSucceeds(studentDb.collection("users").doc(STUDENT_UID).get());
});

test("Approved student can query batch courses, but student cannot create a course", async () => {
  await seedAdminAndCourse();
  const studentDb = testEnv.authenticatedContext(STUDENT_UID).firestore();
  await assertSucceeds(studentDb.collection("courses").where("batchId", "==", "eng_2026").get());
  await assertFails(
    studentDb.collection("courses").doc("course_new").set({
      title: "كيمياء",
      code: "CHEM101",
      academicYear: 1,
      term: 1,
      instructorName: "د. سلمى",
      department: "هندسة",
      accentHex: "#0D9488",
      batchId: "eng_2026",
      createdBy: STUDENT_UID,
      createdAt: nowTimestamp(),
      updatedAt: nowTimestamp(),
    })
  );
});

test("TA can upload material only to assigned course, denied on unassigned course", async () => {
  await seedAdminAndCourse();
  const taDb = testEnv.authenticatedContext(TA_UID).firestore();

  await assertSucceeds(
    taDb.collection("materials").doc("mat_math_1").set({
      courseId: "course_math",
      courseTitle: "الرياضيات الهندسية",
      category: "lectures",
      title: "المحاضرة الأولى",
      description: "مقدمة التكامل",
      weekNumber: 1,
      fileFormat: "PDF",
      fileSize: "2.1 MB",
      url: "https://example.com/lec1.pdf",
      batchId: "eng_2026",
      uploaderId: TA_UID,
      uploaderName: "م. طارق",
      createdAt: nowTimestamp(),
      updatedAt: nowTimestamp(),
    })
  );

  await assertFails(
    taDb.collection("materials").doc("mat_phys_1").set({
      courseId: "course_phys",
      courseTitle: "الفيزياء",
      category: "lectures",
      title: "محاضرة غير مصرحة",
      description: "اختبار",
      weekNumber: 1,
      fileFormat: "PDF",
      fileSize: "1 MB",
      url: "https://example.com/phys.pdf",
      batchId: "eng_2026",
      uploaderId: TA_UID,
      uploaderName: "م. طارق",
      createdAt: nowTimestamp(),
      updatedAt: nowTimestamp(),
    })
  );
});

test("Student can manage own assignment progress but cannot access another user's progress", async () => {
  await seedAdminAndCourse();
  const studentDb = testEnv.authenticatedContext(STUDENT_UID).firestore();
  await assertSucceeds(
    studentDb.collection("users").doc(STUDENT_UID).collection("assignment_progress").doc("assign_1").set({
      userId: STUDENT_UID,
      assignmentId: "assign_1",
      completed: true,
      submissionFileName: "sheet1.pdf",
      submissionFileType: "PDF",
      submissionNote: "تم الحل",
      submissionUri: "content://docs/1",
      submittedAtMillis: 1700000000000,
      createdAt: nowTimestamp(),
      updatedAt: nowTimestamp(),
    })
  );

  const taDb = testEnv.authenticatedContext(TA_UID).firestore();
  await assertFails(
    taDb.collection("users").doc(STUDENT_UID).collection("assignment_progress").get()
  );
});

test("Shadow update with ghost field is rejected", async () => {
  await seedAdminAndCourse();
  const studentDb = testEnv.authenticatedContext(STUDENT_UID).firestore();
  await assertFails(
    studentDb.collection("users").doc(STUDENT_UID).update({
      darkMode: true,
      ghostField: "hacked",
      updatedAt: nowTimestamp(),
    })
  );
});

test("Only Dean can publish circular announcement; BatchRep is denied circular priority", async () => {
  await seedAdminAndCourse();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc("dean_uid").set(baseUserProfile("dean_uid", {
      displayName: "عميد الكلية",
      role: "dean",
      approvalStatus: "approved",
      batchId: "eng_2026",
    }));
  });

  const repDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  // ADMIN_UID in seedAdminAndCourse has admins/{ADMIN_UID} marker, which grants isAdmin()->isDean().
  // Create a non-marker batch_rep user to test batch_rep vs dean separation:
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc("rep_only").set(baseUserProfile("rep_only", {
      displayName: "ممثل الدفعة",
      role: "batch_rep",
      approvalStatus: "approved",
      batchId: "eng_2026",
    }));
  });
  const plainRepDb = testEnv.authenticatedContext("rep_only").firestore();
  await assertFails(
    plainRepDb.collection("announcements").doc("circ_1").set({
      title: "تعميم جامعي",
      body: "تفاصيل التعميم",
      priority: "circular",
      batchId: "eng_2026",
      authorId: "rep_only",
      authorName: "ممثل الدفعة",
      createdAt: nowTimestamp(),
      updatedAt: nowTimestamp(),
    })
  );

  const deanDb = testEnv.authenticatedContext("dean_uid").firestore();
  await assertSucceeds(
    deanDb.collection("announcements").doc("circ_1").set({
      title: "تعميم جامعي رسمي",
      body: "موعد الامتحانات النهائية",
      priority: "circular",
      batchId: "eng_2026",
      authorId: "dean_uid",
      authorName: "عميد الكلية",
      createdAt: nowTimestamp(),
      updatedAt: nowTimestamp(),
    })
  );
  // Keep unused repDb referenced
  await assertSucceeds(repDb.collection("courses").where("batchId", "==", "eng_2026").get());
});

test("Student can record study session and update own leaderboard, but cannot tamper other user's leaderboard or dynamic_modules", async () => {
  await seedAdminAndCourse();
  const studentDb = testEnv.authenticatedContext(STUDENT_UID).firestore();

  await assertSucceeds(
    studentDb.collection("users").doc(STUDENT_UID).collection("study_sessions").doc("sess_1").set({
      userId: STUDENT_UID,
      mode: "pomodoro",
      courseTitle: "الرياضيات الهندسية",
      durationSeconds: 1500,
      pointsEarned: 25,
      completedAtMillis: 1700000000000,
      createdAt: nowTimestamp(),
      updatedAt: nowTimestamp(),
    })
  );

  await assertSucceeds(
    studentDb.collection("study_leaderboard").doc(STUDENT_UID).set({
      userId: STUDENT_UID,
      displayName: "سارة علي",
      department: "هندسة الحاسبات",
      academicYear: 2,
      role: "student",
      batchId: "eng_2026",
      focusPoints: 25,
      totalStudySeconds: 1500,
      pomodoroSessionsCompleted: 1,
      longestSessionSeconds: 1500,
      createdAt: nowTimestamp(),
      updatedAt: nowTimestamp(),
    })
  );

  await assertFails(
    studentDb.collection("study_leaderboard").doc(TA_UID).set({
      userId: TA_UID,
      displayName: "م. طارق",
      department: "هندسة",
      academicYear: 2,
      role: "ta",
      batchId: "eng_2026",
      focusPoints: 9999,
      totalStudySeconds: 9999,
      pomodoroSessionsCompleted: 10,
      longestSessionSeconds: 1500,
      createdAt: nowTimestamp(),
      updatedAt: nowTimestamp(),
    })
  );

  await assertFails(
    studentDb.collection("dynamic_modules").doc("mod_1").set({
      title: "قسم مخترق",
      subtitle: "تجربة",
      moduleType: "home_card",
      iconKey: "star",
      accentHex: "#1E3A8A",
      actionType: "navigate_courses",
      actionPayload: "",
      sortOrder: 1,
      enabled: true,
      batchId: "eng_2026",
      createdBy: STUDENT_UID,
      createdByName: "سارة علي",
      createdAt: nowTimestamp(),
      updatedAt: nowTimestamp(),
    })
  );
});

test("Approved students can create and collaborate in mini meeting_rooms", async () => {
  await seedAdminAndCourse();
  const studentDb = testEnv.authenticatedContext(STUDENT_UID).firestore();
  const taDb = testEnv.authenticatedContext(TA_UID).firestore();

  await assertSucceeds(
    studentDb.collection("meeting_rooms").doc("room_1").set({
      title: "مراجعة الميكانيكا",
      topic: "حل مسائل العزوم",
      courseTitle: "الميكانيكا الهندسية",
      batchId: "eng_2026",
      hostId: STUDENT_UID,
      hostName: "سارة علي",
      status: "active",
      participantUids: [STUDENT_UID],
      participantNames: ["سارة علي"],
      raisedHandUids: [],
      agendaNotes: "بند 1: مراجعة القوانين",
      meetingUrl: "",
      startedAtMillis: 1700000000000,
      createdAt: nowTimestamp(),
      updatedAt: nowTimestamp(),
    })
  );

  await assertSucceeds(
    taDb.collection("meeting_rooms").doc("room_1").update({
      participantUids: [STUDENT_UID, TA_UID],
      participantNames: ["سارة علي", "م. طارق"],
      agendaNotes: "بند 1: مراجعة القوانين\nبند 2: حل الشيت",
      updatedAt: nowTimestamp(),
    })
  );
});

test("Student can manage own spaced-repetition flashcards and personal To-Do tasks, isolated from other users", async () => {
  await seedAdminAndCourse();
  const studentDb = testEnv.authenticatedContext(STUDENT_UID).firestore();
  const taDb = testEnv.authenticatedContext(TA_UID).firestore();

  await assertSucceeds(
    studentDb.collection("users").doc(STUDENT_UID).collection("flashcards").doc("card_1").set({
      userId: STUDENT_UID,
      courseId: "course_math",
      courseTitle: "الرياضيات الهندسية",
      deckName: "المعادلات التفاضلية",
      frontText: "ما هو حل معادلة أويلر؟",
      backText: "تُحوَّل إلى معادلة خطية بمعاملات ثابتة بالتعويض x = e^t",
      difficultyRating: "new",
      easeFactorPercent: 250,
      intervalDays: 0,
      repetitions: 0,
      reviewCount: 0,
      nextReviewAtMillis: 1700000000000,
      createdAt: nowTimestamp(),
      updatedAt: nowTimestamp(),
    })
  );

  await assertSucceeds(
    studentDb.collection("users").doc(STUDENT_UID).collection("flashcards").doc("card_1").update({
      difficultyRating: "hard",
      easeFactorPercent: 230,
      intervalDays: 0,
      repetitions: 0,
      reviewCount: 1,
      nextReviewAtMillis: 1700000060000,
      lastReviewedAtMillis: 1700000000000,
      updatedAt: nowTimestamp(),
    })
  );

  await assertFails(
    taDb.collection("users").doc(STUDENT_UID).collection("flashcards").get()
  );

  await assertSucceeds(
    studentDb.collection("users").doc(STUDENT_UID).collection("personal_tasks").doc("todo_1").set({
      userId: STUDENT_UID,
      title: "مراجعة شيت الفيزياء",
      notes: "مسائل الفصل الثالث",
      category: "مذاكرة",
      courseTitle: "الفيزياء",
      priority: "high",
      dueAtMillis: 1700100000000,
      completed: false,
      createdAt: nowTimestamp(),
      updatedAt: nowTimestamp(),
    })
  );

  await assertFails(
    studentDb.collection("users").doc(STUDENT_UID).collection("personal_tasks").doc("todo_bad").set({
      userId: STUDENT_UID,
      title: "مهمة مخالفة",
      category: "مذاكرة",
      priority: "critical",
      dueAtMillis: 1700100000000,
      completed: false,
      createdAt: nowTimestamp(),
      updatedAt: nowTimestamp(),
    })
  );

  await assertFails(
    taDb.collection("users").doc(STUDENT_UID).collection("personal_tasks").get()
  );
});

test("User can update preferredLanguage and streak fields; approved members can read/create global_references", async () => {
  await seedAdminAndCourse();
  const studentDb = testEnv.authenticatedContext(STUDENT_UID).firestore();
  const taDb = testEnv.authenticatedContext(TA_UID).firestore();

  await assertSucceeds(
    studentDb.collection("users").doc(STUDENT_UID).update({
      preferredLanguage: "en",
      currentStreakDays: 5,
      longestStreakDays: 9,
      lastStudyDayKey: "2026-11-12",
      completedPersonalTasksCount: 4,
      updatedAt: nowTimestamp(),
    })
  );

  await assertSucceeds(
    studentDb.collection("study_leaderboard").doc(STUDENT_UID).set({
      userId: STUDENT_UID,
      displayName: "سارة علي",
      department: "هندسة الحاسبات",
      academicYear: 2,
      role: "student",
      batchId: "eng_2026",
      focusPoints: 120,
      totalStudySeconds: 7200,
      pomodoroSessionsCompleted: 4,
      longestSessionSeconds: 3600,
      currentStreakDays: 5,
      createdAt: nowTimestamp(),
      updatedAt: nowTimestamp(),
    })
  );

  await assertSucceeds(
    studentDb.collection("global_references").doc("ref_mit").set({
      title: "MIT OpenCourseWare - Differential Equations",
      provider: "MIT OCW",
      subject: "Mathematics",
      description: "Full lecture notes and video series",
      url: "https://ocw.mit.edu/courses/18-03-differential-equations-spring-2010/",
      languageCode: "en",
      batchId: "eng_2026",
      createdBy: STUDENT_UID,
      createdAt: nowTimestamp(),
      updatedAt: nowTimestamp(),
    })
  );

  await assertSucceeds(taDb.collection("global_references").where("batchId", "==", "eng_2026").get());
  await assertFails(taDb.collection("global_references").doc("ref_mit").delete());
});


