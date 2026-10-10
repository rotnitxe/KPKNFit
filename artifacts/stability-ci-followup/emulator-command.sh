export PATH="$(dirname "$KPKN_FUNCTIONS_NODE"):$PATH"
"$KPKN_FUNCTIONS_NODE" node_modules/firebase-tools/lib/bin/firebase.js emulators:exec --project demo-kpkn-stability --only firestore,auth,storage,functions "cd functions && \"$KPKN_RULES_NODE\" --test --test-concurrency=1 integration/*.test.mjs"
