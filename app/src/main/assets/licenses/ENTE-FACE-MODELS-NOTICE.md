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

## What each model does

### YOLOv5Face Small detector

The detector analyzes a full photo and proposes face bounding boxes plus five facial landmarks (eye, nose and mouth-corner positions). NewG's future inference stage must apply the detector's expected 640 × 640 preprocessing and decode its output correctly. Non-Maximum Suppression (NMS) is then used to remove duplicate overlapping detections. The detector locates faces; it does not identify people.

### Face alignment

Detected face crops are aligned from their five landmarks using a geometric similarity transform (rotation, scale and translation). This is preprocessing code, not a third model file. Alignment makes the subsequent face comparison less sensitive to pose and position.

### MobileFaceNet embedder

The aligned face crop is passed to MobileFaceNet. It produces a numeric face embedding intended for comparing face similarity. The embedding is not a person's name and does not itself create People groups. NewG must implement the exact input normalization, tensor dimensions, output normalization and similarity metric expected by this ONNX export.

## Intended NewG usage

The intended pipeline is:

1. Read eligible gallery images on-device.
2. Run YOLOv5Face and decode detections.
3. Apply NMS and align each retained face using landmarks.
4. Run MobileFaceNet and persist embeddings with the source media ID and face geometry.
5. Cluster similar embeddings into People groups and expose user correction controls.

Only steps 1–4 are prerequisites for producing usable face indexes; clustering and People UI are separate follow-up work. The current PR implements **model downloading and integrity checks only**. It does not yet execute either ONNX model, index photos, generate embeddings, or create People groups.

## Runtime and privacy

The repository already includes ONNX Runtime Android. The planned runtime will load the downloaded files from app-private storage. After models are installed, inference can be performed locally and does not require a model download connection. Photo access must respect NewG's gallery permissions and exclusions; this downloader itself does not read or upload photos. Any future persistence/sync behavior for face embeddings must be designed separately and must not be inferred from Ente's own server-sync implementation.
