Drop the native Android model bundle here for debug/sideload builds.

Expected filenames:
- kpkn-food-fg270m-v1.litertlm
- kpkn-food-fg270m-v1.task

The app copies the model into app-private storage on first warmup and never reads it from the WebView assets path.
