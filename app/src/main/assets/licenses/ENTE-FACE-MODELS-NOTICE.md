# Ente face-model assets

NewG downloads two ONNX model files from the public Ente Photos FlutterFace demonstration repository:

- Face detector: `yolov5s_face_640_640.onnx` (32,369,479 bytes)
- Face embedder: `mobilefacenet_opset15.onnx` (5,286,998 bytes)

Upstream project: https://github.com/laurenspriem/flutterface
Pinned source commit: https://github.com/laurenspriem/flutterface/commit/dbcbc2b183b516a859323ee7bd5ed269aa9eeaa0
Upstream project license: GNU General Public License v3.0 (GPL-3.0); see [ENTE-FLUTTERFACE-GPL-3.0.txt](ENTE-FLUTTERFACE-GPL-3.0.txt).

The downloaded artifacts are verified against their upstream Git blob object IDs (SHA-1, including Git's `blob <length>\\0` header) and exact byte lengths before installation:

| File | Bytes | Git blob object ID |
|---|---:|---|
| `yolov5s_face_640_640.onnx` | 32,369,479 | `11c42e4909997c9dab9eb318ba919fe10110ee16` |
| `mobilefacenet_opset15.onnx` | 5,286,998 | `d5468a5e0df5209ed088db9d6eef4a7eb36c97f2` |

The source project describes these models as YOLOv5Face for face detection and MobileFaceNet for embeddings. This downloader only installs the model artifacts; it does not copy FlutterFace's Dart implementation into NewG.
