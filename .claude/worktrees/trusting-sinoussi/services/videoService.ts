// services/videoService.ts

let mediaRecorder: MediaRecorder | null = null;
let recordedChunks: Blob[] = [];

/**
 * Starts recording video from the user's camera.
 * @param videoElement The HTMLVideoElement to display the stream on.
 * @returns A promise that resolves when recording starts.
 */
export const startRecording = async (videoElement: HTMLVideoElement): Promise<void> => {
  if (mediaRecorder && mediaRecorder.state === "recording") {
    console.warn("Recording is already in progress.");
    return;
  }

  try {
    const stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true });
    videoElement.srcObject = stream;
    videoElement.play();

    recordedChunks = [];
    mediaRecorder = new MediaRecorder(stream);

    mediaRecorder.ondataavailable = (event) => {
      if (event.data.size > 0) {
        recordedChunks.push(event.data);
      }
    };

    mediaRecorder.start();
  } catch (error) {
    console.error("Error starting video recording:", error);
    throw new Error("No se pudo acceder a la cámara. Asegúrate de tener los permisos necesarios.");
  }
};

/**
 * Stops the current recording.
 * @param videoElement The HTMLVideoElement that was displaying the stream.
 * @returns A promise that resolves with the recorded video as a base64 Data URL.
 */
export const stopRecording = (videoElement: HTMLVideoElement): Promise<string | null> => {
  return new Promise((resolve) => {
    if (!mediaRecorder || mediaRecorder.state === "inactive") {
      console.warn("No recording is active to stop.");
      resolve(null);
      return;
    }

    mediaRecorder.onstop = () => {
      const blob = new Blob(recordedChunks, { type: 'video/webm' });
      const reader = new FileReader();
      reader.onloadend = () => {
        resolve(reader.result as string);
      };
      reader.readAsDataURL(blob);

      // Clean up the stream
      const stream = videoElement.srcObject as MediaStream;
      stream.getTracks().forEach(track => track.stop());
      videoElement.srcObject = null;
      mediaRecorder = null;
    };

    mediaRecorder.stop();
  });
};