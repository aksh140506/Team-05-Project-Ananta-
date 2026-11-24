const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function buildFacilityPayload(uid) {
  const now = new Date();
  return {
    userId: uid,
    updatedByUid: uid,
    nameEn: "Ramesh Kirana BC Point",
    nameHi: "रमेश किराना बीसी केंद्र",
    facilityType: "BC_POINT",
    villageOrArea: "Rampur Chowk",
    distanceMeters: 420,
    isOpen: true,
    cashStatus: "CASH_READY",
    lastReportStatus: "GOT_CASH",
    reportsCount: 4,
    isActive: true,
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated user cannot read or list facilities or checklists", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("facilities").where("isActive", "==", true).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("checklists").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("scamChecks").get());
});

test("Authenticated user can create, list with isActive filter, and update status of a facility", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb.collection("facilities").doc("fac_bc_rampur");
  await assertSucceeds(docRef.set(buildFacilityPayload(ALICE_UID)));

  // Query aligned with rule fails without isActive filter
  await assertFails(aliceDb.collection("facilities").get());
  // Query with isActive == true succeeds
  await assertSucceeds(aliceDb.collection("facilities").where("isActive", "==", true).get());

  // Bob can update operational status fields only
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(
    bobDb.collection("facilities").doc("fac_bc_rampur").update({
      isOpen: true,
      cashStatus: "LOW_CASH",
      lastReportStatus: "LONG_LINE",
      reportsCount: 5,
      updatedByUid: BOB_UID,
      updatedAt: new Date(),
    })
  );

  // Bob cannot alter immutable nameEn or inject shadow field
  await assertFails(
    bobDb.collection("facilities").doc("fac_bc_rampur").update({
      nameEn: "Hijacked Bank",
      updatedByUid: BOB_UID,
      updatedAt: new Date(),
    })
  );
  await assertFails(
    bobDb.collection("facilities").doc("fac_bc_rampur").update({
      isVerifiedAdmin: true,
      updatedByUid: BOB_UID,
      updatedAt: new Date(),
    })
  );
});

test("Authenticated user can post valid facility report, but cannot spoof userId or update report", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("facilities").doc("fac_bc_rampur").set(buildFacilityPayload(ALICE_UID)));

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(
    bobDb.collection("facilities").doc("fac_bc_rampur").collection("reports").doc("rep_1").set({
      userId: BOB_UID,
      facilityId: "fac_bc_rampur",
      status: "GOT_CASH",
      receiptReceived: true,
      isPublic: true,
      createdAt: new Date(),
    })
  );

  // Spoofed userId fails
  await assertFails(
    bobDb.collection("facilities").doc("fac_bc_rampur").collection("reports").doc("rep_2").set({
      userId: ALICE_UID,
      facilityId: "fac_bc_rampur",
      status: "GOT_CASH",
      isPublic: true,
      createdAt: new Date(),
    })
  );

  // Update fails (append-only)
  await assertFails(
    bobDb.collection("facilities").doc("fac_bc_rampur").collection("reports").doc("rep_1").update({
      status: "NO_CASH_CLOSED",
    })
  );
});

test("Private user checklists and scamChecks enforce strict cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("checklists").doc("chk_withdraw").set({
      userId: ALICE_UID,
      taskType: "WITHDRAW",
      amountInr: 3000,
      recommendedFacilityType: "BC_POINT",
      checkedDocIds: ["aadhaar"],
      receiptVerified: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("checklists").where("userId", "==", ALICE_UID).get()
  );

  // Bob cannot read or write Alice's checklists
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("checklists").doc("chk_withdraw").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("checklists").where("userId", "==", ALICE_UID).get()
  );

  // Alice can log a scam check; Bob cannot read it
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("scamChecks").doc("scam_1").set({
      userId: ALICE_UID,
      inputSnippet: "Share OTP to verify KYC immediately",
      riskLevel: "HIGH_RISK",
      explanationEn: "Never share an OTP over phone or SMS.",
      explanationHi: "कभी भी फोन या एसएमएस पर ओटीपी साझा न करें।",
      sourceMode: "GEMINI_AI",
      createdAt: now,
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("scamChecks").doc("scam_1").get()
  );
});
