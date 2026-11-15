# Ente face-model assets and NewG integration

NewG downloads two ONNX model files from the public Ente Photos FlutterFace demonstration repository:

- Face detector: `yolov5s_face_640_640.onnx` (32,369,479 bytes)
- Face embedder: `mobilefacenet_opset15.onnx` (5,286,998 bytes)

Upstream project: https://github.com/laurenspriem/flutterface
Pinned source commit: https://github.com/laurenspriem/flutterface/commit/dbcbc2b183b516a859323ee7bd5ed269aa9eeaa0
Upstream project license: GNU General Public License v3.0 (GPL-3.0); see [ENTE-FLUTTERFACE-GPL-3.0.txt](ENTE-FLUTTERFACE-GPL-3.0.txt).

The downloaded artifacts are verified against their upstream Git blob object IDs (SHA-1, including Git's `blob <length>\0` header) and exact byte lengths before installation:

| File | Bytes | Git blob object ID |
|---|---:|---|
| `yolov5s_face_640_640.onnx` | 32,369,479 | `11c42e4909997c9dab9eb318ba919fe10110ee16` |
| `mobilefacenet_opset15.onnx` | 5,286,998 | `d5468a5e0df5209ed088db9d6eef4a7eb36c97f2` |

The FlutterFace repository's GPL-3.0 license covers its source code. Do not assume that this automatically settles the separate licensing terms of the downloaded model weights; review any model-specific terms from the upstream authors before distributing the application.

## Model roles and tensor contracts

### YOLOv5Face Small detector

The detector analyzes a full photo and proposes face bounding boxes plus five facial landmarks (left eye, right eye, nose, and two mouth corners). NewG letterboxes images to 640 × 640, converts RGB pixels to float values in [0, 1], and supplies an NCHW tensor. The pinned model export returns 25,200 candidate rows with 16 values per row. NewG filters low-confidence rows and applies Non-Maximum Suppression (NMS) at an IoU threshold of 0.4 to remove duplicate overlapping detections. The detector locates faces; it does not identify people.

### Face alignment

NewG maps the five detected landmarks to the 112 × 112 ArcFace reference template with a least-squares 2D similarity transform (uniform scale, rotation and translation). This is preprocessing code, not a third model file.

### MobileFaceNet embedder

Each aligned crop is resized to 112 × 112 and passed as an NHWC RGB float tensor normalized to [-1, 1]. MobileFaceNet returns a 192-value face embedding, which NewG L2-normalizes before storing. An embedding is a numeric representation for similarity comparison; it is not a person's name or identity by itself.

## NewG face-index workflow

1. The user downloads and verifies both model files from AI Engine & Models settings.
2. Re-index Library reads eligible photo records already in NewG's gallery database. Videos, Hidden Vault items and trashed items are excluded.
3. Each photo is decoded locally, respecting its EXIF orientation, and passed through YOLOv5Face, NMS, five-point alignment and MobileFaceNet.
4. Face boxes, landmarks and normalized embeddings are stored as JSON in the corresponding local Room media row. Per-photo progress is persisted, so a paused run can continue with remaining photos. Reset clears the local face index.
5. Similarity clustering, People albums, names and manual correction controls are not implemented in this change.

## Runtime and privacy

NewG uses ONNX Runtime Android to load the downloaded files from app-private storage. Inference and face-index persistence happen on-device; this indexer does not upload photos or embeddings. It processes only items already indexed in NewG's local gallery database and does not request additional media permissions. Model download itself requires an internet connection; inference after installation does not.

The face-index JSON is local application data. Any future backup, export or synchronization of face embeddings must be explicitly designed and disclosed rather than inferred from Ente's own implementation.
