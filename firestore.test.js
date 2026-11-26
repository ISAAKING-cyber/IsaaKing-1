const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_creator_1";
const BOB_UID = "bob_collab_2";
const EVE_UID = "eve_outsider_3";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validProjectPayload(ownerId = ALICE_UID, inviteCode = "ISK9A1B2C") {
  const now = new Date(Date.now() - 2000);
  return {
    ownerId,
    ownerName: "Alice Creator",
    title: "Neon Cyber Reel",
    aspectRatio: "9:16",
    resolution: "1080p",
    fps: 60,
    inviteCode,
    memberUids: [ownerId],
    activeEditors: ["Alice (Pixel 9)"],
    clips: [
      {
        clipId: "clip_01",
        label: "Neon Cybercity",
        sourceType: "sample_cybercity",
        localUri: "",
        totalDurationMs: 5000,
        trimStartMs: 0,
        trimEndMs: 4500,
        speed: 1.0,
        volume: 1.0,
        reversed: false,
        transitionOutId: "dissolve",
        transitionDurationMs: 700,
        orderIndex: 0,
      },
    ],
    captions: [],
    textOverlays: [],
    totalDurationMs: 4500,
    createdAt: now,
    updatedAt: now,
  };
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
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("projects").where("memberUids", "array-contains", ALICE_UID).get());
  await assertFails(unauth.collection("users").doc(ALICE_UID).get());
  await assertFails(unauth.collection("project_invites").doc("ISK9A1B2C").get());
  await assertFails(unauth.collection("projects").doc("proj_1").set(validProjectPayload()));
});

test("User profile isolation: owner can create/update, cross-user denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      photoUrl: "",
      deviceName: "Pixel 9",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Hijacked",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Owner can create project, invite, edit timeline, query member projects, and log activity", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const payload = validProjectPayload(ALICE_UID, "ISK9A1B2C");

  await assertSucceeds(aliceDb.collection("projects").doc("proj_1").set(payload));
  await assertSucceeds(
    aliceDb.collection("project_invites").doc("ISK9A1B2C").set({
      projectId: "proj_1",
      ownerId: ALICE_UID,
      projectTitle: "Neon Cyber Reel",
      active: true,
      createdAt: new Date(Date.now() - 1000),
    })
  );

  // Filtered Kotlin query succeeds
  await assertSucceeds(
    aliceDb.collection("projects").where("memberUids", "array-contains", ALICE_UID).get()
  );

  // Unfiltered list query fails
  await assertFails(aliceDb.collection("projects").get());

  // Partial update mirroring ProjectRepository.saveProjectEdits
  await assertSucceeds(
    aliceDb.collection("projects").doc("proj_1").update({
      title: "Neon Cyber Cut v2",
      aspectRatio: "16:9",
      resolution: "4K",
      fps: 60,
      activeEditors: ["Alice (Pixel 9)"],
      clips: payload.clips,
      captions: [
        {
          captionId: "cap_1",
          text: "Welcome to Isaaking",
          startMs: 0,
          endMs: 1800,
          styleId: "hormozi_pop",
          highlightColorHex: "#00E5FF",
        },
      ],
      textOverlays: [],
      musicTrack: {
        trackId: "trk_neon_pulse",
        title: "Midnight Telemetry",
        artist: "KAIRO",
        genre: "Cyberpunk Synth",
        bpm: 124,
        durationMs: 32000,
        rootMidiNote: 57,
        volume: 0.75,
        fadeInMs: 500,
        fadeOutMs: 800,
        startOffsetMs: 0,
      },
      globalFilter: {
        filterId: "cyber_neon",
        name: "Neon Rain",
        prompt: "Vibrant cyan glow",
        brightness: -0.04,
        contrast: 1.32,
        saturation: 1.35,
        temperature: -0.35,
        tintHex: "#00E5FF",
        tintAlpha: 0.12,
        vignette: 0.35,
        aiGenerated: true,
      },
      totalDurationMs: 4500,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Activity subcollection write + filtered query
  await assertSucceeds(
    aliceDb.collection("projects").doc("proj_1").collection("activity").doc("act_1").set({
      projectId: "proj_1",
      actorUid: ALICE_UID,
      actorName: "Alice",
      deviceName: "Pixel 9",
      actionSummary: "Applied Neon Rain AI filter",
      memberUids: [ALICE_UID],
      createdAt: new Date(Date.now() - 400),
    })
  );
  await assertSucceeds(
    aliceDb.collection("projects").doc("proj_1").collection("activity")
      .where("memberUids", "array-contains", ALICE_UID).get()
  );
});

test("Collaborator can join via invite code and co-edit; outsider denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();

  await assertSucceeds(aliceDb.collection("projects").doc("proj_collab").set(validProjectPayload(ALICE_UID, "ISKCOLLAB1")));
  await assertSucceeds(
    aliceDb.collection("project_invites").doc("ISKCOLLAB1").set({
      projectId: "proj_collab",
      ownerId: ALICE_UID,
      projectTitle: "Neon Cyber Reel",
      active: true,
      createdAt: new Date(Date.now() - 1000),
    })
  );

  // Invite enumeration is forbidden
  await assertFails(eveDb.collection("project_invites").get());

  // Outsider cannot read project directly
  await assertFails(eveDb.collection("projects").doc("proj_collab").get());

  // Bob looks up invite by exact code and joins memberUids
  await assertSucceeds(bobDb.collection("project_invites").doc("ISKCOLLAB1").get());
  await assertSucceeds(
    bobDb.collection("projects").doc("proj_collab").update({
      memberUids: [ALICE_UID, BOB_UID],
      activeEditors: ["Alice (Pixel 9)", "Bob (Tablet)"],
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Now Bob can read & edit timeline
  await assertSucceeds(bobDb.collection("projects").doc("proj_collab").get());
  await assertSucceeds(
    bobDb.collection("projects").doc("proj_collab").update({
      title: "Co-Edited Cyber Reel",
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Bob cannot delete Alice's project
  await assertFails(bobDb.collection("projects").doc("proj_collab").delete());

  // Eve still cannot join with fake/wrong invite or tamper with ownerId/ghost fields
  await assertFails(
    eveDb.collection("projects").doc("proj_collab").update({
      ownerId: EVE_UID,
      updatedAt: new Date(Date.now() - 100),
    })
  );
  await assertFails(
    aliceDb.collection("projects").doc("proj_bad").set({
      ...validProjectPayload(ALICE_UID, "ISKBAD12"),
      id: "proj_bad", // Ghost @DocumentId key must be rejected
    })
  );
  await assertFails(
    aliceDb.collection("projects").doc("proj_future").set({
      ...validProjectPayload(ALICE_UID, "ISKFUT12"),
      createdAt: new Date(Date.now() + 60000), // Future timestamp rejected
    })
  );
});
