# Third-party components

AvaCore's own code is licensed under the **GNU GPL v3.0 or later** (see `LICENSE`).
The APK also bundles these components, each under its own terms:

| Component | Where | License |
| :--- | :--- | :--- |
| [eSpeak NG](https://github.com/espeak-ng/espeak-ng) (engine, inside the Sherpa-ONNX native library, and `assets/tts/espeak-ng-data/`) | phonemizer | GPL-3.0-or-later — the reason AvaCore itself is GPL |
| [Sherpa-ONNX](https://github.com/k2-fsa/sherpa-onnx) (`app/libs/sherpa-onnx.aar`) | inference engine | Apache-2.0 |
| [ONNX Runtime](https://github.com/microsoft/onnxruntime) (inside the Sherpa-ONNX AAR) | inference runtime | MIT |
| Piper voice `fa_IR-gyro-medium` ([model card](https://huggingface.co/rhasspy/piper-voices/tree/main/fa/fa_IR/gyro/medium)) (`assets/tts/model_fa.onnx`, `tokens_fa.txt`) | Persian voice | the model card says "License: See URL" and points to its author, [gyroing](https://github.com/gyroing) — no explicit license confirmed yet |

Development-only tools under `tools/` download reference dictionaries at run
time to measure pronunciation (e.g. tihudict, GPL-3.0); none of that data is
committed or shipped.
