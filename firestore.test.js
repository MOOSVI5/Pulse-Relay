const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_001";
const BOB_UID = "bob_uid_002";

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

function validAccountSettings(uid, ts) {
  return {
    userId: uid,
    accountSid: "AC7a4f92e1b80d4c3e91a5f2",
    planTier: "UNLIMITED_DIRECT",
    unlimitedTexts: true,
    directRouting: true,
    apiServerPort: 8765,
    defaultWebhookUrl: "https://example.com/webhooks/sms",
    totalMessagesSent: 0,
    totalOtpVerified: 0,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validApiKey(uid, ts) {
  return {
    userId: uid,
    label: "Production Web App",
    secretKey: "pr_live_8f92a3b4c5d6e7f8a9b0c1d2",
    environment: "LIVE",
    scopes: "SMS,VERIFY,NUMBERS",
    active: true,
    requestsCount: 0,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validPhoneNumber(uid, ts) {
  return {
    userId: uid,
    e164Number: "+14158904321",
    countryCode: "US",
    countryName: "United States",
    regionLabel: "San Francisco, US",
    numberType: "LOCAL",
    capabilities: "SMS,OTP,MMS",
    monthlyPriceUsd: 0.0,
    webhookUrl: "",
    defaultSender: true,
    status: "ACTIVE",
    createdAt: ts,
    updatedAt: ts,
  };
}

function validSmsMessage(uid, ts) {
  return {
    userId: uid,
    sid: "SM8a9b0c1d2e3f4a5b",
    direction: "OUTBOUND_API",
    fromNumber: "+14158904321",
    toNumber: "+447700900412",
    countryCode: "GB",
    carrierName: "Vodafone UK",
    body: "Your PulseRelay code is 492817",
    segments: 1,
    status: "QUEUED",
    latencyMs: 0,
    priceUsd: 0.0,
    apiKeyLabel: "Production Web App",
    webhookStatus: "NONE",
    createdAt: ts,
    updatedAt: ts,
  };
}

function validOtp(uid, ts) {
  return {
    userId: uid,
    sid: "VE4c5d6e7f8a9b0c1d",
    serviceName: "PulseAuth Global",
    toNumber: "+447700900412",
    fromNumber: "+14158904321",
    code: "492817",
    channel: "SMS",
    status: "PENDING",
    attempts: 0,
    maxAttempts: 5,
    expiresAtMillis: Date.now() + 300000,
    createdAt: ts,
    updatedAt: ts,
  };
}

test("Unauthenticated users are denied across all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const ts = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set(validAccountSettings(ALICE_UID, ts)));
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("api_keys").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("phone_numbers").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("sms_messages").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("otp_verifications").get());
});

test("AccountSettings: owner create, valid partial update, immutable & ghost field rejections", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = new Date(Date.now() - 5000);
  const docRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(docRef.set(validAccountSettings(ALICE_UID, ts)));
  await assertSucceeds(docRef.get());

  await assertSucceeds(
    docRef.update({
      planTier: "PAY_AS_YOU_GO",
      unlimitedTexts: false,
      totalMessagesSent: 1,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Immutable accountSid tampering fails
  await assertFails(
    docRef.update({
      accountSid: "AC_TAMPERED",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Delete forbidden
  await assertFails(docRef.delete());

  // Future timestamp fails
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set(validAccountSettings(BOB_UID, new Date(Date.now() + 60000)))
  );
});

test("API Keys & Phone Numbers: owner CRUD succeeds, @DocumentId id injection & cross-user access fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date(Date.now() - 5000);

  const keyRef = aliceDb.collection("users").doc(ALICE_UID).collection("api_keys").doc("key_1");
  await assertSucceeds(keyRef.set(validApiKey(ALICE_UID, ts)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("api_keys").get());
  await assertSucceeds(
    keyRef.update({
      active: false,
      requestsCount: 4,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // @DocumentId `id` field must be rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("api_keys").doc("key_2").set({
      id: "key_2",
      ...validApiKey(ALICE_UID, ts),
    })
  );

  const numRef = aliceDb.collection("users").doc(ALICE_UID).collection("phone_numbers").doc("num_1");
  await assertSucceeds(numRef.set(validPhoneNumber(ALICE_UID, ts)));
  await assertSucceeds(
    numRef.update({
      webhookUrl: "https://api.myapp.dev/sms",
      status: "PAUSED",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Cross-user read & write fail
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("api_keys").doc("key_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("phone_numbers").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("phone_numbers").doc("num_1").update({
      status: "PAUSED",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("SMS Messages & OTP Verifications: lifecycle updates & terminal state lock", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = new Date(Date.now() - 5000);

  const msgRef = aliceDb.collection("users").doc(ALICE_UID).collection("sms_messages").doc("msg_1");
  await assertSucceeds(msgRef.set(validSmsMessage(ALICE_UID, ts)));
  await assertSucceeds(
    msgRef.update({
      status: "DELIVERED",
      latencyMs: 118,
      webhookStatus: "DISPATCHED_200",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  const otpRef = aliceDb.collection("users").doc(ALICE_UID).collection("otp_verifications").doc("otp_1");
  await assertSucceeds(otpRef.set(validOtp(ALICE_UID, ts)));

  // Wrong attempt while PENDING succeeds
  await assertSucceeds(
    otpRef.update({
      attempts: 1,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Approve OTP (reaches terminal state)
  await assertSucceeds(
    otpRef.update({
      status: "APPROVED",
      verifiedAtMillis: Date.now(),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Subsequent mutation on terminal APPROVED OTP must fail
  await assertFails(
    otpRef.update({
      status: "PENDING",
      attempts: 0,
      updatedAt: new Date(Date.now() - 100),
    })
  );
});

test("GitHub Integrations: owner CRUD succeeds, immutable repo/owner & cross-user access fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date(Date.now() - 5000);

  const ghRef = aliceDb.collection("users").doc(ALICE_UID).collection("github_integrations").doc("gh_1");
  await assertSucceeds(
    ghRef.set({
      userId: ALICE_UID,
      githubUsername: "octocat",
      repoFullName: "octocat/Hello-World",
      notifyToNumber: "+14155550199",
      triggerMode: "COMMIT_SMS",
      lastCommitSha: "7fd1a60",
      lastCommitMessage: "Initial commit",
      active: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("github_integrations").get());

  await assertSucceeds(
    ghRef.update({
      triggerMode: "DEPLOY_OTP",
      lastCommitSha: "b3f9a21",
      lastCommitMessage: "Release v2",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Immutable repoFullName tampering fails
  await assertFails(
    ghRef.update({
      repoFullName: "evil/hijack",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Cross-user read & write fail
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("github_integrations").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("github_integrations").doc("gh_1").delete());
});

test("Team Profiles: owner CRUD, role updates, ownerAdmin protection & cross-user denial", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date(Date.now() - 5000);

  const adminRef = aliceDb.collection("users").doc(ALICE_UID).collection("team_profiles").doc("profile_admin");
  await assertSucceeds(
    adminRef.set({
      userId: ALICE_UID,
      displayName: "Admin",
      contactHandle: "owner@example.com",
      role: "ADMIN",
      ownerAdmin: true,
      active: true,
      dailyOtpLimit: 10000,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Owner Admin cannot be downgraded or deleted
  await assertFails(
    adminRef.update({
      role: "VIEWER",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(adminRef.delete());

  // Team member profile create, role update, and delete succeed
  const memberRef = aliceDb.collection("users").doc(ALICE_UID).collection("team_profiles").doc("member_1");
  await assertSucceeds(
    memberRef.set({
      userId: ALICE_UID,
      displayName: "Ali Khan",
      contactHandle: "+923001234567",
      role: "OTP_OPERATOR",
      ownerAdmin: false,
      active: true,
      dailyOtpLimit: 500,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("team_profiles").get());
  await assertSucceeds(
    memberRef.update({
      role: "DEVELOPER",
      active: false,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertSucceeds(memberRef.delete());

  // Invalid role fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("team_profiles").doc("member_bad").set({
      userId: ALICE_UID,
      displayName: "Bad Role",
      contactHandle: "",
      role: "SUPERUSER",
      ownerAdmin: false,
      active: true,
      dailyOtpLimit: 100,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Cross-user access fails
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("team_profiles").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("team_profiles").doc("profile_admin").get());
});
