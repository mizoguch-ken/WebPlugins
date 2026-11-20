package ken.mizoguch.webviewer.webcam;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebEngine;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javax.imageio.ImageIO;
import ken.mizoguch.webviewer.plugin.WebViewerPlugin;
import ken.mizoguch.webviewer.plugin.kit.SandboxPath;
import ken.mizoguch.webviewer.plugin.kit.ScriptValue;
import ken.mizoguch.webviewer.plugin.kit.ServiceSupport;
import netscape.javascript.JSException;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.Java2DFrameUtils;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.javacv.OpenCVFrameGrabber;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_dnn;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Size;
import org.bytedeco.videoinput.videoInput;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author mizoguch_ken
 */
public class WebCam extends ServiceSupport<Void> {

    private static final String FUNCTION_NAME = "webcam";

    private volatile WebEngine webEngine_;
    private volatile Worker.State state_;

    /**
     * a camera the computer holds, told to the page
     */
    private record WebcamInfo(int index, String name) {
    }

    /**
     * the values which turn the image of the camera into the paper of a model
     */
    private static final class BlobInfo {

        double scalefactor;
        Size size;
        Scalar mean;
        boolean swapRB;
        boolean crop;
        int ddepth;
    }

    private volatile OpenCVFrameGrabber webcam_;

    private final Stage webcamStage_;
    private final BorderPane webcamPane_;
    private final ImageView webcamImageView_;
    private final ObjectProperty<Image> imageProperty_;

    private volatile String funcResultImage_;
    private volatile String funcResultBlob_;
    private volatile String webcamImageType_;
    private Size webcamImageSize_;
    private volatile int webcamImageColor_;
    private volatile String webcamImageColorName_;
    private volatile double frameRate_;
    private volatile String resultImage_;
    private volatile String resultImageBase64_;
    private volatile String resultBlob_;
    private volatile BlobInfo blobInfo_;
    private final AtomicReference<CompletableFuture<String>> snapshotRequest_ = new AtomicReference<>();

    private volatile boolean isPlay_;

    /**
     * the capture tasks which read the grabber of the camera : the device is freed only when no
     * task reads it anymore, so the grabber of a task which is running is not closed under it
     */
    private final AtomicInteger runningTasks_ = new AtomicInteger();

    /**
     * maximum time the cleanup waits a capture task which does not end
     */
    private static final int TASK_TERMINATION_MILLIS = 3000;
    private volatile boolean isUpdateWebcamView_;
    private volatile boolean isUpdateFuncResultImage_;
    private volatile boolean isUpdateFuncResultBlob_;
    private volatile String saveFilePath_;

    private final Gson gson_ = new Gson();

    /**
     *
     */
    public WebCam() {
        webEngine_ = null;
        state_ = Worker.State.READY;

        webcam_ = null;

        webcamStage_ = new Stage(StageStyle.DECORATED);
        webcamPane_ = new BorderPane();
        webcamImageView_ = new ImageView();
        webcamImageView_.setPreserveRatio(true);
        webcamPane_.setCenter(webcamImageView_);
        webcamStage_.setTitle("WebCam");
        webcamStage_.setScene(new Scene(webcamPane_));
        webcamStage_.setResizable(false);
        imageProperty_ = new SimpleObjectProperty<>();
        webcamImageView_.imageProperty().bind(imageProperty_);
        webcamImageSize_ = null;
        webcamImageColor_ = -1;
        webcamImageColorName_ = null;
        frameRate_ = 0.0;
        webcamImageType_ = "bmp";

        blobInfo_ = null;
        funcResultImage_ = null;
        funcResultBlob_ = null;
        resultImage_ = null;
        resultImageBase64_ = null;
        resultBlob_ = null;

        isPlay_ = false;
        isUpdateWebcamView_ = false;
        isUpdateFuncResultImage_ = false;
        saveFilePath_ = null;
    }

    /**
     *
     */
    public void licenses() {
        new Licenses().show();
    }

    /**
     *
     * @param func
     */
    public void setNotifyResultImage(String func) {
        funcResultImage_ = scriptFunction(func);
    }

    /**
     *
     * @param func
     * @param blobInfo
     */
    public void setNotifyResultBlob(String func, String blobInfo) {
        funcResultBlob_ = scriptFunction(func);

        if (blobInfo_ == null) {
            blobInfo_ = new BlobInfo();
        }
        if (blobInfo == null) {
            return;
        }

        try {
            JsonObject info = gson_.fromJson(blobInfo, JsonObject.class);
            if (info == null) {
                return;
            }
            BlobInfo prev = blobInfo_;
            BlobInfo next = new BlobInfo();
            next.scalefactor = prev.scalefactor;
            next.size = (prev.size == null) ? null : new Size(prev.size.width(), prev.size.height());
            next.mean = prev.mean;
            next.swapRB = prev.swapRB;
            next.crop = prev.crop;
            next.ddepth = prev.ddepth;

            if (info.has("scalefactor")) {
                next.scalefactor = info.getAsJsonPrimitive("scalefactor").getAsDouble();
            }

            if (info.has("size")) {
                JsonObject size = info.getAsJsonObject("size");

                if ((size.has("width")) && size.has("height")) {
                    int width = size.getAsJsonPrimitive("width").getAsInt();
                    int height = size.getAsJsonPrimitive("height").getAsInt();
                    if ((width < 0) || (height < 0)) {
                        throw new IllegalArgumentException("Incorrect blob size [" + width + "x" + height + "]");
                    }
                    next.size = new Size(width, height);
                }
            }

            if (info.has("mean")) {
                JsonArray mean = info.getAsJsonArray("mean");
                switch (mean.size()) {
                    case 1:
                        next.mean = new Scalar(mean.get(0).getAsDouble());
                        break;
                    case 2:
                        next.mean = new Scalar(mean.get(0).getAsDouble(), mean.get(1).getAsDouble());
                        break;
                    case 3:
                        next.mean = new Scalar(mean.get(0).getAsDouble(), mean.get(1).getAsDouble(),
                                mean.get(2).getAsDouble(), 0);
                        break;
                    case 4:
                        next.mean = new Scalar(mean.get(0).getAsDouble(), mean.get(1).getAsDouble(),
                                mean.get(2).getAsDouble(), mean.get(3).getAsDouble());
                        break;
                    default:
                        break;
                }
            }

            if (info.has("swapRB")) {
                next.swapRB = info.getAsJsonPrimitive("swapRB").getAsBoolean();
            }

            if (info.has("crop")) {
                next.crop = info.getAsJsonPrimitive("crop").getAsBoolean();
            }

            if (info.has("ddepth")) {
                switch (info.getAsJsonPrimitive("ddepth").getAsString()) {
                    case "CV_8U":
                        next.ddepth = opencv_core.CV_8U;
                        break;
                    case "CV_8S":
                        next.ddepth = opencv_core.CV_8S;
                        break;
                    case "CV_16U":
                        next.ddepth = opencv_core.CV_16U;
                        break;
                    case "CV_16S":
                        next.ddepth = opencv_core.CV_16S;
                        break;
                    case "CV_32S":
                        next.ddepth = opencv_core.CV_32S;
                        break;
                    case "CV_32F":
                        next.ddepth = opencv_core.CV_32F;
                        break;
                    case "CV_64F":
                        next.ddepth = opencv_core.CV_64F;
                        break;
                    case "CV_16F":
                        next.ddepth = opencv_core.CV_16F;
                        break;
                }
            }
            blobInfo_ = next;
        } catch (JsonSyntaxException | IllegalArgumentException | IllegalStateException
                | UnsupportedOperationException | ClassCastException | NullPointerException ex) {
            writeStackTrace(FUNCTION_NAME, ex);
        }
    }

    /**
     *
     * @return
     */
    public String getWebcams() {
        /* the library of the camera writes the list of the devices it found in the stream of the
        virtual machine when it is not asked to keep quiet, which disturbs the reader of that stream */
        int idx = videoInput.listDevices(true);
        List<WebcamInfo> webcamInfos = new ArrayList<>();

        for (int i = 0; i < idx; i++) {
            BytePointer deviceName = videoInput.getDeviceName(i);
            webcamInfos.add(new WebcamInfo(i, (deviceName != null) ? deviceName.getString() : ""));
        }

        return gson_.toJson(webcamInfos);
    }

    /**
     *
     * @return
     */
    public String getImageType() {
        return webcamImageType_;
    }

    /**
     *
     * @param type
     * @return
     */
    public Boolean setImageType(String type) {
        if (type != null) {
            type = type.trim().toLowerCase(Locale.ROOT);
            String[] suffixes = ImageIO.getWriterFileSuffixes();

            for (String suffixe : suffixes) {
                if (suffixe.equals(type)) {
                    webcamImageType_ = suffixe;
                    return true;
                }
            }
            return false;
        }
        return null;
    }

    /**
     *
     * @return
     */
    public String getImageSize() {
        Size size = webcamImageSize_;
        if (size != null) {
            JsonObject obj = new JsonObject();
            obj.addProperty("width", size.width());
            obj.addProperty("height", size.height());
            return gson_.toJson(obj);
        }
        return null;
    }

    /**
     *
     * @param width
     * @param height
     */
    public void setImageSize(int width, int height) {
        if ((width <= 0) || (height <= 0)) {
            write(FUNCTION_NAME, "Incorrect image size [" + width + "x" + height + "]", true);
            return;
        }
        if (webcamImageSize_ == null) {
            webcamImageSize_ = new Size();
        }

        webcamImageSize_.width(width);
        webcamImageSize_.height(height);
        webcamImageView_.setFitWidth(width);
        webcamImageView_.setFitHeight(height);
        webcamStage_.sizeToScene();
    }

    /**
     *
     * @return
     */
    public Integer getCaptureWidth() {
        OpenCVFrameGrabber webcam = webcam_;
        if (webcam != null) {
            return webcam.getImageWidth();
        }
        write(FUNCTION_NAME, "Camera device is not open", true);
        return null;
    }

    /**
     *
     * @return
     */
    public Integer getCaptureHeight() {
        OpenCVFrameGrabber webcam = webcam_;
        if (webcam != null) {
            return webcam.getImageHeight();
        }
        write(FUNCTION_NAME, "Camera device is not open", true);
        return null;
    }

    /**
     *
     * @param fps
     * @return
     */
    public Boolean setFrameRate(Double fps) {
        if ((fps == null) || !Double.isFinite(fps) || (fps < 0.0)) {
            write(FUNCTION_NAME, "Incorrect frame rate [" + fps + "]", true);
            return false;
        }
        frameRate_ = fps;
        return true;
    }

    /**
     *
     * @return
     */
    public Double getFrameRate() {
        return frameRate_;
    }

    /**
     *
     * @return
     */
    public int getImageColor() {
        return webcamImageColor_;
    }

    /**
     *
     * @param color
     */
    public void setImageColor(String color) {
        if ((color == null) || color.trim().isEmpty() || "NONE".equalsIgnoreCase(color.trim())) {
            webcamImageColor_ = -1;
            webcamImageColorName_ = null;
            return;
        }
        switch (color) {
            case "COLOR_BGR2BGRA":
                webcamImageColor_ = opencv_imgproc.COLOR_BGR2BGRA;
                break;
            case "COLOR_RGB2RGBA":
                webcamImageColor_ = opencv_imgproc.COLOR_RGB2RGBA;
                break;
            case "COLOR_BGRA2BGR":
                webcamImageColor_ = opencv_imgproc.COLOR_BGRA2BGR;
                break;
            case "COLOR_RGBA2RGB":
                webcamImageColor_ = opencv_imgproc.COLOR_RGBA2RGB;
                break;
            case "COLOR_BGR2RGBA":
                webcamImageColor_ = opencv_imgproc.COLOR_BGR2RGBA;
                break;
            case "COLOR_RGB2BGRA":
                webcamImageColor_ = opencv_imgproc.COLOR_RGB2BGRA;
                break;
            case "COLOR_RGBA2BGR":
                webcamImageColor_ = opencv_imgproc.COLOR_RGBA2BGR;
                break;
            case "COLOR_BGRA2RGB":
                webcamImageColor_ = opencv_imgproc.COLOR_BGRA2RGB;
                break;
            case "COLOR_BGR2RGB":
                webcamImageColor_ = opencv_imgproc.COLOR_BGR2RGB;
                break;
            case "COLOR_RGB2BGR":
                webcamImageColor_ = opencv_imgproc.COLOR_RGB2BGR;
                break;
            case "COLOR_BGRA2RGBA":
                webcamImageColor_ = opencv_imgproc.COLOR_BGRA2RGBA;
                break;
            case "COLOR_RGBA2BGRA":
                webcamImageColor_ = opencv_imgproc.COLOR_RGBA2BGRA;
                break;
            case "COLOR_BGR2GRAY":
                webcamImageColor_ = opencv_imgproc.COLOR_BGR2GRAY;
                break;
            case "COLOR_RGB2GRAY":
                webcamImageColor_ = opencv_imgproc.COLOR_RGB2GRAY;
                break;
            case "COLOR_GRAY2BGR":
                webcamImageColor_ = opencv_imgproc.COLOR_GRAY2BGR;
                break;
            case "COLOR_GRAY2RGB":
                webcamImageColor_ = opencv_imgproc.COLOR_GRAY2RGB;
                break;
            case "COLOR_GRAY2BGRA":
                webcamImageColor_ = opencv_imgproc.COLOR_GRAY2BGRA;
                break;
            case "COLOR_GRAY2RGBA":
                webcamImageColor_ = opencv_imgproc.COLOR_GRAY2RGBA;
                break;
            case "COLOR_BGRA2GRAY":
                webcamImageColor_ = opencv_imgproc.COLOR_BGRA2GRAY;
                break;
            case "COLOR_RGBA2GRAY":
                webcamImageColor_ = opencv_imgproc.COLOR_RGBA2GRAY;
                break;
            case "COLOR_BGR2BGR565":
                webcamImageColor_ = opencv_imgproc.COLOR_BGR2BGR565;
                break;
            case "COLOR_RGB2BGR565":
                webcamImageColor_ = opencv_imgproc.COLOR_RGB2BGR565;
                break;
            case "COLOR_BGR5652BGR":
                webcamImageColor_ = opencv_imgproc.COLOR_BGR5652BGR;
                break;
            case "COLOR_BGR5652RGB":
                webcamImageColor_ = opencv_imgproc.COLOR_BGR5652RGB;
                break;
            case "COLOR_BGRA2BGR565":
                webcamImageColor_ = opencv_imgproc.COLOR_BGRA2BGR565;
                break;
            case "COLOR_RGBA2BGR565":
                webcamImageColor_ = opencv_imgproc.COLOR_RGBA2BGR565;
                break;
            case "COLOR_BGR5652BGRA":
                webcamImageColor_ = opencv_imgproc.COLOR_BGR5652BGRA;
                break;
            case "COLOR_BGR5652RGBA":
                webcamImageColor_ = opencv_imgproc.COLOR_BGR5652RGBA;
                break;
            case "COLOR_GRAY2BGR565":
                webcamImageColor_ = opencv_imgproc.COLOR_GRAY2BGR565;
                break;
            case "COLOR_BGR5652GRAY":
                webcamImageColor_ = opencv_imgproc.COLOR_BGR5652GRAY;
                break;
            case "COLOR_BGR2BGR555":
                webcamImageColor_ = opencv_imgproc.COLOR_BGR2BGR555;
                break;
            case "COLOR_RGB2BGR555":
                webcamImageColor_ = opencv_imgproc.COLOR_RGB2BGR555;
                break;
            case "COLOR_BGR5552BGR":
                webcamImageColor_ = opencv_imgproc.COLOR_BGR5552BGR;
                break;
            case "COLOR_BGR5552RGB":
                webcamImageColor_ = opencv_imgproc.COLOR_BGR5552RGB;
                break;
            case "COLOR_BGRA2BGR555":
                webcamImageColor_ = opencv_imgproc.COLOR_BGRA2BGR555;
                break;
            case "COLOR_RGBA2BGR555":
                webcamImageColor_ = opencv_imgproc.COLOR_RGBA2BGR555;
                break;
            case "COLOR_BGR5552BGRA":
                webcamImageColor_ = opencv_imgproc.COLOR_BGR5552BGRA;
                break;
            case "COLOR_BGR5552RGBA":
                webcamImageColor_ = opencv_imgproc.COLOR_BGR5552RGBA;
                break;
            case "COLOR_GRAY2BGR555":
                webcamImageColor_ = opencv_imgproc.COLOR_GRAY2BGR555;
                break;
            case "COLOR_BGR5552GRAY":
                webcamImageColor_ = opencv_imgproc.COLOR_BGR5552GRAY;
                break;
            case "COLOR_BGR2XYZ":
                webcamImageColor_ = opencv_imgproc.COLOR_BGR2XYZ;
                break;
            case "COLOR_RGB2XYZ":
                webcamImageColor_ = opencv_imgproc.COLOR_RGB2XYZ;
                break;
            case "COLOR_XYZ2BGR":
                webcamImageColor_ = opencv_imgproc.COLOR_XYZ2BGR;
                break;
            case "COLOR_XYZ2RGB":
                webcamImageColor_ = opencv_imgproc.COLOR_XYZ2RGB;
                break;
            case "COLOR_BGR2YCrCb":
                webcamImageColor_ = opencv_imgproc.COLOR_BGR2YCrCb;
                break;
            case "COLOR_RGB2YCrCb":
                webcamImageColor_ = opencv_imgproc.COLOR_RGB2YCrCb;
                break;
            case "COLOR_YCrCb2BGR":
                webcamImageColor_ = opencv_imgproc.COLOR_YCrCb2BGR;
                break;
            case "COLOR_YCrCb2RGB":
                webcamImageColor_ = opencv_imgproc.COLOR_YCrCb2RGB;
                break;
            case "COLOR_BGR2HSV":
                webcamImageColor_ = opencv_imgproc.COLOR_BGR2HSV;
                break;
            case "COLOR_RGB2HSV":
                webcamImageColor_ = opencv_imgproc.COLOR_RGB2HSV;
                break;
            case "COLOR_BGR2Lab":
                webcamImageColor_ = opencv_imgproc.COLOR_BGR2Lab;
                break;
            case "COLOR_RGB2Lab":
                webcamImageColor_ = opencv_imgproc.COLOR_RGB2Lab;
                break;
            case "COLOR_BGR2Luv":
                webcamImageColor_ = opencv_imgproc.COLOR_BGR2Luv;
                break;
            case "COLOR_RGB2Luv":
                webcamImageColor_ = opencv_imgproc.COLOR_RGB2Luv;
                break;
            case "COLOR_BGR2HLS":
                webcamImageColor_ = opencv_imgproc.COLOR_BGR2HLS;
                break;
            case "COLOR_RGB2HLS":
                webcamImageColor_ = opencv_imgproc.COLOR_RGB2HLS;
                break;
            case "COLOR_HSV2BGR":
                webcamImageColor_ = opencv_imgproc.COLOR_HSV2BGR;
                break;
            case "COLOR_HSV2RGB":
                webcamImageColor_ = opencv_imgproc.COLOR_HSV2RGB;
                break;
            case "COLOR_Lab2BGR":
                webcamImageColor_ = opencv_imgproc.COLOR_Lab2BGR;
                break;
            case "COLOR_Lab2RGB":
                webcamImageColor_ = opencv_imgproc.COLOR_Lab2RGB;
                break;
            case "COLOR_Luv2BGR":
                webcamImageColor_ = opencv_imgproc.COLOR_Luv2BGR;
                break;
            case "COLOR_Luv2RGB":
                webcamImageColor_ = opencv_imgproc.COLOR_Luv2RGB;
                break;
            case "COLOR_HLS2BGR":
                webcamImageColor_ = opencv_imgproc.COLOR_HLS2BGR;
                break;
            case "COLOR_HLS2RGB":
                webcamImageColor_ = opencv_imgproc.COLOR_HLS2RGB;
                break;
            case "COLOR_BGR2HSV_FULL":
                webcamImageColor_ = opencv_imgproc.COLOR_BGR2HSV_FULL;
                break;
            case "COLOR_RGB2HSV_FULL":
                webcamImageColor_ = opencv_imgproc.COLOR_RGB2HSV_FULL;
                break;
            case "COLOR_BGR2HLS_FULL":
                webcamImageColor_ = opencv_imgproc.COLOR_BGR2HLS_FULL;
                break;
            case "COLOR_RGB2HLS_FULL":
                webcamImageColor_ = opencv_imgproc.COLOR_RGB2HLS_FULL;
                break;
            case "COLOR_HSV2BGR_FULL":
                webcamImageColor_ = opencv_imgproc.COLOR_HSV2BGR_FULL;
                break;
            case "COLOR_HSV2RGB_FULL":
                webcamImageColor_ = opencv_imgproc.COLOR_HSV2RGB_FULL;
                break;
            case "COLOR_HLS2BGR_FULL":
                webcamImageColor_ = opencv_imgproc.COLOR_HLS2BGR_FULL;
                break;
            case "COLOR_HLS2RGB_FULL":
                webcamImageColor_ = opencv_imgproc.COLOR_HLS2RGB_FULL;
                break;
            case "COLOR_LBGR2Lab":
                webcamImageColor_ = opencv_imgproc.COLOR_LBGR2Lab;
                break;
            case "COLOR_LRGB2Lab":
                webcamImageColor_ = opencv_imgproc.COLOR_LRGB2Lab;
                break;
            case "COLOR_LBGR2Luv":
                webcamImageColor_ = opencv_imgproc.COLOR_LBGR2Luv;
                break;
            case "COLOR_LRGB2Luv":
                webcamImageColor_ = opencv_imgproc.COLOR_LRGB2Luv;
                break;
            case "COLOR_Lab2LBGR":
                webcamImageColor_ = opencv_imgproc.COLOR_Lab2LBGR;
                break;
            case "COLOR_Lab2LRGB":
                webcamImageColor_ = opencv_imgproc.COLOR_Lab2LRGB;
                break;
            case "COLOR_Luv2LBGR":
                webcamImageColor_ = opencv_imgproc.COLOR_Luv2LBGR;
                break;
            case "COLOR_Luv2LRGB":
                webcamImageColor_ = opencv_imgproc.COLOR_Luv2LRGB;
                break;
            case "COLOR_BGR2YUV":
                webcamImageColor_ = opencv_imgproc.COLOR_BGR2YUV;
                break;
            case "COLOR_RGB2YUV":
                webcamImageColor_ = opencv_imgproc.COLOR_RGB2YUV;
                break;
            case "COLOR_YUV2BGR":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2BGR;
                break;
            case "COLOR_YUV2RGB":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2RGB;
                break;
            case "COLOR_YUV2RGB_NV12":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2RGB_NV12;
                break;
            case "COLOR_YUV2BGR_NV12":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2BGR_NV12;
                break;
            case "COLOR_YUV2RGB_NV21":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2RGB_NV21;
                break;
            case "COLOR_YUV2BGR_NV21":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2BGR_NV21;
                break;
            case "COLOR_YUV420sp2RGB":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV420sp2RGB;
                break;
            case "COLOR_YUV420sp2BGR":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV420sp2BGR;
                break;
            case "COLOR_YUV2RGBA_NV12":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2RGBA_NV12;
                break;
            case "COLOR_YUV2BGRA_NV12":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2BGRA_NV12;
                break;
            case "COLOR_YUV2RGBA_NV21":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2RGBA_NV21;
                break;
            case "COLOR_YUV2BGRA_NV21":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2BGRA_NV21;
                break;
            case "COLOR_YUV420sp2RGBA":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV420sp2RGBA;
                break;
            case "COLOR_YUV420sp2BGRA":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV420sp2BGRA;
                break;
            case "COLOR_YUV2RGB_YV12":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2RGB_YV12;
                break;
            case "COLOR_YUV2BGR_YV12":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2BGR_YV12;
                break;
            case "COLOR_YUV2RGB_IYUV":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2RGB_IYUV;
                break;
            case "COLOR_YUV2BGR_IYUV":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2BGR_IYUV;
                break;
            case "COLOR_YUV2RGB_I420":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2RGB_I420;
                break;
            case "COLOR_YUV2BGR_I420":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2BGR_I420;
                break;
            case "COLOR_YUV420p2RGB":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV420p2RGB;
                break;
            case "COLOR_YUV420p2BGR":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV420p2BGR;
                break;
            case "COLOR_YUV2RGBA_YV12":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2RGBA_YV12;
                break;
            case "COLOR_YUV2BGRA_YV12":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2BGRA_YV12;
                break;
            case "COLOR_YUV2RGBA_IYUV":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2RGBA_IYUV;
                break;
            case "COLOR_YUV2BGRA_IYUV":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2BGRA_IYUV;
                break;
            case "COLOR_YUV2RGBA_I420":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2RGBA_I420;
                break;
            case "COLOR_YUV2BGRA_I420":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2BGRA_I420;
                break;
            case "COLOR_YUV420p2RGBA":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV420p2RGBA;
                break;
            case "COLOR_YUV420p2BGRA":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV420p2BGRA;
                break;
            case "COLOR_YUV2GRAY_420":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2GRAY_420;
                break;
            case "COLOR_YUV2GRAY_NV21":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2GRAY_NV21;
                break;
            case "COLOR_YUV2GRAY_NV12":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2GRAY_NV12;
                break;
            case "COLOR_YUV2GRAY_YV12":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2GRAY_YV12;
                break;
            case "COLOR_YUV2GRAY_IYUV":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2GRAY_IYUV;
                break;
            case "COLOR_YUV2GRAY_I420":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2GRAY_I420;
                break;
            case "COLOR_YUV420sp2GRAY":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV420sp2GRAY;
                break;
            case "COLOR_YUV420p2GRAY":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV420p2GRAY;
                break;
            case "COLOR_YUV2RGB_UYVY":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2RGB_UYVY;
                break;
            case "COLOR_YUV2BGR_UYVY":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2BGR_UYVY;
                break;
            case "COLOR_YUV2RGB_Y422":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2RGB_Y422;
                break;
            case "COLOR_YUV2BGR_Y422":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2BGR_Y422;
                break;
            case "COLOR_YUV2RGB_UYNV":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2RGB_UYNV;
                break;
            case "COLOR_YUV2BGR_UYNV":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2BGR_UYNV;
                break;
            case "COLOR_YUV2RGBA_UYVY":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2RGBA_UYVY;
                break;
            case "COLOR_YUV2BGRA_UYVY":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2BGRA_UYVY;
                break;
            case "COLOR_YUV2RGBA_Y422":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2RGBA_Y422;
                break;
            case "COLOR_YUV2BGRA_Y422":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2BGRA_Y422;
                break;
            case "COLOR_YUV2RGBA_UYNV":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2RGBA_UYNV;
                break;
            case "COLOR_YUV2BGRA_UYNV":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2BGRA_UYNV;
                break;
            case "COLOR_YUV2RGB_YUY2":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2RGB_YUY2;
                break;
            case "COLOR_YUV2BGR_YUY2":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2BGR_YUY2;
                break;
            case "COLOR_YUV2RGB_YVYU":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2RGB_YVYU;
                break;
            case "COLOR_YUV2BGR_YVYU":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2BGR_YVYU;
                break;
            case "COLOR_YUV2RGB_YUYV":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2RGB_YUYV;
                break;
            case "COLOR_YUV2BGR_YUYV":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2BGR_YUYV;
                break;
            case "COLOR_YUV2RGB_YUNV":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2RGB_YUNV;
                break;
            case "COLOR_YUV2BGR_YUNV":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2BGR_YUNV;
                break;
            case "COLOR_YUV2RGBA_YUY2":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2RGBA_YUY2;
                break;
            case "COLOR_YUV2BGRA_YUY2":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2BGRA_YUY2;
                break;
            case "COLOR_YUV2RGBA_YVYU":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2RGBA_YVYU;
                break;
            case "COLOR_YUV2BGRA_YVYU":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2BGRA_YVYU;
                break;
            case "COLOR_YUV2RGBA_YUYV":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2RGBA_YUYV;
                break;
            case "COLOR_YUV2BGRA_YUYV":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2BGRA_YUYV;
                break;
            case "COLOR_YUV2RGBA_YUNV":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2RGBA_YUNV;
                break;
            case "COLOR_YUV2BGRA_YUNV":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2BGRA_YUNV;
                break;
            case "COLOR_YUV2GRAY_UYVY":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2GRAY_UYVY;
                break;
            case "COLOR_YUV2GRAY_YUY2":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2GRAY_YUY2;
                break;
            case "COLOR_YUV2GRAY_Y422":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2GRAY_Y422;
                break;
            case "COLOR_YUV2GRAY_UYNV":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2GRAY_UYNV;
                break;
            case "COLOR_YUV2GRAY_YVYU":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2GRAY_YVYU;
                break;
            case "COLOR_YUV2GRAY_YUYV":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2GRAY_YUYV;
                break;
            case "COLOR_YUV2GRAY_YUNV":
                webcamImageColor_ = opencv_imgproc.COLOR_YUV2GRAY_YUNV;
                break;
            case "COLOR_RGBA2mRGBA":
                webcamImageColor_ = opencv_imgproc.COLOR_RGBA2mRGBA;
                break;
            case "COLOR_mRGBA2RGBA":
                webcamImageColor_ = opencv_imgproc.COLOR_mRGBA2RGBA;
                break;
            case "COLOR_RGB2YUV_I420":
                webcamImageColor_ = opencv_imgproc.COLOR_RGB2YUV_I420;
                break;
            case "COLOR_BGR2YUV_I420":
                webcamImageColor_ = opencv_imgproc.COLOR_BGR2YUV_I420;
                break;
            case "COLOR_RGB2YUV_IYUV":
                webcamImageColor_ = opencv_imgproc.COLOR_RGB2YUV_IYUV;
                break;
            case "COLOR_BGR2YUV_IYUV":
                webcamImageColor_ = opencv_imgproc.COLOR_BGR2YUV_IYUV;
                break;
            case "COLOR_RGBA2YUV_I420":
                webcamImageColor_ = opencv_imgproc.COLOR_RGBA2YUV_I420;
                break;
            case "COLOR_BGRA2YUV_I420":
                webcamImageColor_ = opencv_imgproc.COLOR_BGRA2YUV_I420;
                break;
            case "COLOR_RGBA2YUV_IYUV":
                webcamImageColor_ = opencv_imgproc.COLOR_RGBA2YUV_IYUV;
                break;
            case "COLOR_BGRA2YUV_IYUV":
                webcamImageColor_ = opencv_imgproc.COLOR_BGRA2YUV_IYUV;
                break;
            case "COLOR_RGB2YUV_YV12":
                webcamImageColor_ = opencv_imgproc.COLOR_RGB2YUV_YV12;
                break;
            case "COLOR_BGR2YUV_YV12":
                webcamImageColor_ = opencv_imgproc.COLOR_BGR2YUV_YV12;
                break;
            case "COLOR_RGBA2YUV_YV12":
                webcamImageColor_ = opencv_imgproc.COLOR_RGBA2YUV_YV12;
                break;
            case "COLOR_BGRA2YUV_YV12":
                webcamImageColor_ = opencv_imgproc.COLOR_BGRA2YUV_YV12;
                break;
            case "COLOR_BayerBG2BGR":
                webcamImageColor_ = opencv_imgproc.COLOR_BayerBG2BGR;
                break;
            case "COLOR_BayerGB2BGR":
                webcamImageColor_ = opencv_imgproc.COLOR_BayerGB2BGR;
                break;
            case "COLOR_BayerRG2BGR":
                webcamImageColor_ = opencv_imgproc.COLOR_BayerRG2BGR;
                break;
            case "COLOR_BayerGR2BGR":
                webcamImageColor_ = opencv_imgproc.COLOR_BayerGR2BGR;
                break;
            case "COLOR_BayerBG2RGB":
                webcamImageColor_ = opencv_imgproc.COLOR_BayerBG2RGB;
                break;
            case "COLOR_BayerGB2RGB":
                webcamImageColor_ = opencv_imgproc.COLOR_BayerGB2RGB;
                break;
            case "COLOR_BayerRG2RGB":
                webcamImageColor_ = opencv_imgproc.COLOR_BayerRG2RGB;
                break;
            case "COLOR_BayerGR2RGB":
                webcamImageColor_ = opencv_imgproc.COLOR_BayerGR2RGB;
                break;
            case "COLOR_BayerBG2GRAY":
                webcamImageColor_ = opencv_imgproc.COLOR_BayerBG2GRAY;
                break;
            case "COLOR_BayerGB2GRAY":
                webcamImageColor_ = opencv_imgproc.COLOR_BayerGB2GRAY;
                break;
            case "COLOR_BayerRG2GRAY":
                webcamImageColor_ = opencv_imgproc.COLOR_BayerRG2GRAY;
                break;
            case "COLOR_BayerGR2GRAY":
                webcamImageColor_ = opencv_imgproc.COLOR_BayerGR2GRAY;
                break;
            case "COLOR_BayerBG2BGR_VNG":
                webcamImageColor_ = opencv_imgproc.COLOR_BayerBG2BGR_VNG;
                break;
            case "COLOR_BayerGB2BGR_VNG":
                webcamImageColor_ = opencv_imgproc.COLOR_BayerGB2BGR_VNG;
                break;
            case "COLOR_BayerRG2BGR_VNG":
                webcamImageColor_ = opencv_imgproc.COLOR_BayerRG2BGR_VNG;
                break;
            case "COLOR_BayerGR2BGR_VNG":
                webcamImageColor_ = opencv_imgproc.COLOR_BayerGR2BGR_VNG;
                break;
            case "COLOR_BayerBG2RGB_VNG":
                webcamImageColor_ = opencv_imgproc.COLOR_BayerBG2RGB_VNG;
                break;
            case "COLOR_BayerGB2RGB_VNG":
                webcamImageColor_ = opencv_imgproc.COLOR_BayerGB2RGB_VNG;
                break;
            case "COLOR_BayerRG2RGB_VNG":
                webcamImageColor_ = opencv_imgproc.COLOR_BayerRG2RGB_VNG;
                break;
            case "COLOR_BayerGR2RGB_VNG":
                webcamImageColor_ = opencv_imgproc.COLOR_BayerGR2RGB_VNG;
                break;
            case "COLOR_BayerBG2BGR_EA":
                webcamImageColor_ = opencv_imgproc.COLOR_BayerBG2BGR_EA;
                break;
            case "COLOR_BayerGB2BGR_EA":
                webcamImageColor_ = opencv_imgproc.COLOR_BayerGB2BGR_EA;
                break;
            case "COLOR_BayerRG2BGR_EA":
                webcamImageColor_ = opencv_imgproc.COLOR_BayerRG2BGR_EA;
                break;
            case "COLOR_BayerGR2BGR_EA":
                webcamImageColor_ = opencv_imgproc.COLOR_BayerGR2BGR_EA;
                break;
            case "COLOR_BayerBG2RGB_EA":
                webcamImageColor_ = opencv_imgproc.COLOR_BayerBG2RGB_EA;
                break;
            case "COLOR_BayerGB2RGB_EA":
                webcamImageColor_ = opencv_imgproc.COLOR_BayerGB2RGB_EA;
                break;
            case "COLOR_BayerRG2RGB_EA":
                webcamImageColor_ = opencv_imgproc.COLOR_BayerRG2RGB_EA;
                break;
            case "COLOR_BayerGR2RGB_EA":
                webcamImageColor_ = opencv_imgproc.COLOR_BayerGR2RGB_EA;
                break;
            case "COLOR_BayerBG2BGRA":
                webcamImageColor_ = opencv_imgproc.COLOR_BayerBG2BGRA;
                break;
            case "COLOR_BayerGB2BGRA":
                webcamImageColor_ = opencv_imgproc.COLOR_BayerGB2BGRA;
                break;
            case "COLOR_BayerRG2BGRA":
                webcamImageColor_ = opencv_imgproc.COLOR_BayerRG2BGRA;
                break;
            case "COLOR_BayerGR2BGRA":
                webcamImageColor_ = opencv_imgproc.COLOR_BayerGR2BGRA;
                break;
            case "COLOR_BayerBG2RGBA":
                webcamImageColor_ = opencv_imgproc.COLOR_BayerBG2RGBA;
                break;
            case "COLOR_BayerGB2RGBA":
                webcamImageColor_ = opencv_imgproc.COLOR_BayerGB2RGBA;
                break;
            case "COLOR_BayerRG2RGBA":
                webcamImageColor_ = opencv_imgproc.COLOR_BayerRG2RGBA;
                break;
            case "COLOR_BayerGR2RGBA":
                webcamImageColor_ = opencv_imgproc.COLOR_BayerGR2RGBA;
                break;
            case "COLOR_COLORCVT_MAX":
                webcamImageColor_ = opencv_imgproc.COLOR_COLORCVT_MAX;
                break;
            default:
                return;
        }
        webcamImageColorName_ = color;
    }

    /**
     *
     * @return
     */
    public String getImageColorName() {
        return webcamImageColorName_;
    }

    /**
     *
     * @return
     */
    public Boolean isOpened() {
        return (webcam_ != null);
    }

    /**
     *
     * @param deviceNumber
     * @return
     * @throws org.bytedeco.javacv.FrameGrabber.Exception
     */
    public Boolean open(int deviceNumber) throws FrameGrabber.Exception {
        if (webcam_ == null) {
            webcam_ = new OpenCVFrameGrabber(deviceNumber);
            Size configuredSize = webcamImageSize_;
            if ((configuredSize != null) && (configuredSize.width() > 0) && (configuredSize.height() > 0)) {
                webcam_.setImageWidth(configuredSize.width());
                webcam_.setImageHeight(configuredSize.height());
            }
            double fps = frameRate_;
            if (fps > 0.0) {
                webcam_.setFrameRate(fps);
            }
            try {
                webcam_.start();
            } catch (FrameGrabber.Exception ex) {
                // the grabber of a device which can not be used is released and forgotten : the
                // next open() is not refused with the already open message
                try {
                    webcam_.close();
                } catch (FrameGrabber.Exception closeEx) {
                    writeStackTrace(FUNCTION_NAME, closeEx);
                }
                webcam_ = null;
                throw ex;
            }

            if ((webcamImageSize_ == null) || (webcamImageSize_.width() <= 0)
                    || (webcamImageSize_.height() <= 0)) {
                int width = webcam_.getImageWidth();
                int height = webcam_.getImageHeight();
                if ((width > 0) && (height > 0)) {
                    setImageSize(width, height);
                }
            }

            isPlay_ = false;
            return true;
        } else {
            write(FUNCTION_NAME, "Camera device is already open", true);
        }
        return false;
    }

    /**
     *
     * @return
     */
    public Boolean isPlaying() {
        if (webcam_ != null) {
            return isPlay_;
        }
        write(FUNCTION_NAME, "Camera device is not open", true);
        return false;
    }

    /**
     *
     * @return
     */
    public Boolean play() {
        if (webcam_ != null) {
            isPlay_ = true;
            isUpdateWebcamView_ = false;
            isUpdateFuncResultImage_ = false;
            isUpdateFuncResultBlob_ = false;
            if (!this.isRunning()) {
                this.reset();
                this.start();
            }
            return true;
        }
        write(FUNCTION_NAME, "Camera device is not open", true);
        return false;
    }

    /**
     *
     * @return
     */
    public Boolean stop() {
        isPlay_ = false;
        cancelSnapshotRequest();
        return this.cancel();
    }

    /**
     *
     * @param path
     * @return
     * @throws FrameGrabber.Exception
     */
    public Boolean save(String path) throws FrameGrabber.Exception {
        if (webcam_ != null) {
            Path file = SandboxPath.resolve(webPath(), path);
            if ((file == null) || Files.isDirectory(file)) {
                write(FUNCTION_NAME, "Incorrect file path [" + path + "]", true);
                return false;
            }
            if (isPlay_) {
                if (saveFilePath_ == null) {
                    saveFilePath_ = file.toString();
                    return true;
                }
            } else {
                Frame frame = webcam_.grab();

                if (frame != null) {
                    if (frame.image != null) {
                        try (OpenCVFrameConverter.ToMat openCVFrameConverter = new OpenCVFrameConverter.ToMat()) {
                            Mat matImage = openCVFrameConverter.convert(frame);

                            if (matImage != null) {
                                try {
                                    if (webcamImageColor_ >= 0) {
                                        opencv_imgproc.cvtColor(matImage, matImage, webcamImageColor_);
                                    }
                                    return opencv_imgcodecs.imwrite(file.toString(), matImage);
                                } finally {
                                    matImage.release();
                                }
                            }
                        }
                    }
                }
            }
            return false;
        }
        return null;

    }

    /**
     *
     * @return
     * @throws org.bytedeco.javacv.FrameGrabber.Exception
     */
    public String captureBase64() throws FrameGrabber.Exception {
        OpenCVFrameGrabber webcam = webcam_;
        if (webcam == null) {
            write(FUNCTION_NAME, "Camera device is not open", true);
            return null;
        }
        if (isPlay_) {
            CompletableFuture<String> active = snapshotRequest_.get();
            if (active == null) {
                CompletableFuture<String> created = new CompletableFuture<>();
                if (snapshotRequest_.compareAndSet(null, created)) {
                    active = created;
                } else {
                    active = snapshotRequest_.get();
                    if (active == null) {
                        active = created;
                        snapshotRequest_.compareAndSet(null, active);
                    }
                }
            }
            try {
                return active.get(1000L, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                snapshotRequest_.compareAndSet(active, null);
                return null;
            } catch (ExecutionException | TimeoutException ex) {
                snapshotRequest_.compareAndSet(active, null);
                return null;
            }
        }
        Frame frame = webcam.grab();
        if ((frame == null) || (frame.image == null)) {
            return null;
        }
        try (OpenCVFrameConverter.ToMat openCVFrameConverter = new OpenCVFrameConverter.ToMat()) {
            Mat matImage = openCVFrameConverter.convert(frame);
            if (matImage == null) {
                return null;
            }
            try {
                if (webcamImageColor_ >= 0) {
                    opencv_imgproc.cvtColor(matImage, matImage, webcamImageColor_);
                }
                BufferedImage bufferedImage = Java2DFrameUtils.toBufferedImage(matImage);
                if (bufferedImage == null) {
                    return null;
                }
                try {
                    String imageType = effectiveImageType();
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(bufferedImage, imageType, baos);
                    byte[] encoded = baos.toByteArray();
                    if (encoded.length == 0) {
                        return null;
                    }
                    String base64 = Base64.getEncoder().encodeToString(encoded);
                    resultImageBase64_ = base64;
                    return base64;
                } catch (IOException ex) {
                    writeStackTrace(FUNCTION_NAME, ex);
                    return null;
                } finally {
                    bufferedImage.flush();
                }
            } finally {
                matImage.release();
            }
        }
    }

    /**
     *
     * @return
     * @throws org.bytedeco.javacv.FrameGrabber.Exception
     */
    public String captureDataUrl() throws FrameGrabber.Exception {
        String base64 = captureBase64();
        if (base64 == null) {
            return null;
        }
        return "data:" + mimeTypeOf(effectiveImageType()) + ";base64," + base64;
    }

    /**
     *
     * @return
     */
    public String getResultImage() {
        return resultImage_;
    }

    /**
     *
     * @return
     */
    public String getResultImageBase64() {
        return resultImageBase64_;
    }

    /**
     *
     * @return
     */
    public String getResultBlob() {
        return resultBlob_;
    }

    private void cancelSnapshotRequest() {
        CompletableFuture<String> pending = snapshotRequest_.getAndSet(null);
        if (pending != null) {
            pending.complete(null);
        }
    }

    private String effectiveImageType() {
        String imageType = webcamImageType_;
        return ((imageType == null) || imageType.isEmpty()) ? "bmp" : imageType;
    }

    private static String mimeTypeOf(String imageType) {
        if (imageType == null) {
            return "image/bmp";
        }
        switch (imageType.toLowerCase(Locale.ROOT)) {
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "bmp":
                return "image/bmp";
            case "tif":
            case "tiff":
                return "image/tiff";
            case "wbmp":
                return "image/vnd.wap.wbmp";
            default:
                return "image/" + imageType.toLowerCase(Locale.ROOT);
        }
    }

    /**
     *
     * @return
     */
    public Boolean isShowing() {
        if (webcam_ != null) {
            return webcamStage_.isShowing();
        }
        write(FUNCTION_NAME, "Camera device is not open", true);
        return false;
    }

    /**
     *
     * @return
     */
    public Boolean show() {
        if (webcam_ != null) {
            webcamStage_.show();
            return true;
        }
        write(FUNCTION_NAME, "Camera device is not open", true);
        return false;
    }

    /**
     *
     * @return
     */
    public Boolean hide() {
        if (webcam_ != null) {
            webcamStage_.hide();
            return true;
        }
        write(FUNCTION_NAME, "Camera device is not open", true);
        return false;
    }

    /**
     *
     * @return
     */
    public Double getWidth() {
        if (webcam_ != null) {
            return webcamStage_.getWidth();
        }
        write(FUNCTION_NAME, "Camera device is not open", true);
        return null;
    }

    /**
     *
     * @return
     */
    public Double getHeight() {
        if (webcam_ != null) {
            return webcamStage_.getHeight();
        }
        write(FUNCTION_NAME, "Camera device is not open", true);
        return null;
    }

    /**
     *
     * @return
     */
    public Boolean isFocused() {
        if (webcam_ != null) {
            return webcamStage_.isFocused();
        }
        write(FUNCTION_NAME, "Camera device is not open", true);
        return false;
    }

    /**
     *
     * @return
     */
    public Boolean requestFocus() {
        if (webcam_ != null) {
            webcamStage_.requestFocus();
            return true;
        }
        write(FUNCTION_NAME, "Camera device is not open", true);
        return false;
    }

    /**
     *
     * @return
     */
    public Double getOpacity() {
        if (webcam_ != null) {
            return webcamStage_.getOpacity();
        }
        write(FUNCTION_NAME, "Camera device is not open", true);
        return null;
    }

    /**
     *
     * @param value
     * @return
     */
    public Boolean setOpacity(double value) {
        if (webcam_ != null) {
            webcamStage_.setOpacity(value);
            return true;
        }
        write(FUNCTION_NAME, "Camera device is not open", true);
        return false;
    }

    /**
     *
     * @return
     */
    public Boolean isAlwaysOnTop() {
        if (webcam_ != null) {
            return webcamStage_.isAlwaysOnTop();
        }
        write(FUNCTION_NAME, "Camera device is not open", true);
        return false;
    }

    /**
     *
     * @param state
     * @return
     */
    public Boolean setAlwaysOnTop(boolean state) {
        if (webcam_ != null) {
            webcamStage_.setAlwaysOnTop(state);
            return true;
        }
        write(FUNCTION_NAME, "Camera device is not open", true);
        return false;
    }

    /**
     *
     * @return
     */
    public Double getX() {
        if (webcam_ != null) {
            return webcamStage_.getX();
        }
        write(FUNCTION_NAME, "Camera device is not open", true);
        return null;
    }

    /**
     *
     * @param x
     * @return
     */
    public Boolean setX(double x) {
        if (webcam_ != null) {
            webcamStage_.setX(x);
            return true;
        }
        write(FUNCTION_NAME, "Camera device is not open", true);
        return false;
    }

    /**
     *
     * @return
     */
    public Double getY() {
        if (webcam_ != null) {
            return webcamStage_.getY();
        }
        write(FUNCTION_NAME, "Camera device is not open", true);
        return null;
    }

    /**
     *
     * @param y
     * @return
     */
    public Boolean setY(double y) {
        if (webcam_ != null) {
            webcamStage_.setY(y);
            return true;
        }
        write(FUNCTION_NAME, "Camera device is not open", true);
        return false;
    }

    @Override
    protected Task<Void> createTask() {
        return new Task<Void>() {
            @Override
            protected Void call() {
                // the task of the capture is counted while it runs : close() waits it before it
                // frees the camera
                runningTasks_.incrementAndGet();
                try {
                    return capture();
                } finally {
                    runningTasks_.decrementAndGet();
                }
            }

            /**
             * the capture loop of the camera
             *
             * @return null
             */
            private Void capture() {
                Frame frame;
                try (OpenCVFrameConverter.ToMat openCVFrameConverter = new OpenCVFrameConverter.ToMat()) {
                    Mat matImage, matBlob;
                    AtomicReference<WritableImage> webcamImage = new AtomicReference<>();
                    BufferedImage bufferedImage;
                    ByteArrayOutputStream byteArrayImage = new ByteArrayOutputStream();
                    byte[] bytesBlob;
                    long lastFrameNanos = 0L;

                    while (isPlay_) {
                        OpenCVFrameGrabber webcam = webcam_;
                        if (webcam == null) {
                            break;
                        }
                        if ((webcamStage_.isShowing() && !isUpdateWebcamView_)
                                || ((funcResultImage_ != null) && !isUpdateFuncResultImage_)
                                || ((funcResultBlob_ != null) && !isUpdateFuncResultBlob_)
                                || (snapshotRequest_.get() != null)
                                || (saveFilePath_ != null)) {
                            double fps = frameRate_;
                            if (fps > 0.0) {
                                long minIntervalNanos = (long) (1_000_000_000.0 / fps);
                                while (isPlay_ && (lastFrameNanos != 0L)) {
                                    long elapsed = System.nanoTime() - lastFrameNanos;
                                    if (elapsed >= minIntervalNanos) {
                                        break;
                                    }
                                    long sleepMs = Math.min(10L, Math.max(1L,
                                            TimeUnit.NANOSECONDS.toMillis(minIntervalNanos - elapsed)));
                                    try {
                                        TimeUnit.MILLISECONDS.sleep(sleepMs);
                                    } catch (InterruptedException ex) {
                                        Thread.currentThread().interrupt();
                                        return null;
                                    }
                                }
                                if (!isPlay_) {
                                    break;
                                }
                                lastFrameNanos = System.nanoTime();
                            }
                            try {
                                frame = webcam.grab();

                                if (frame != null) {
                                    if (frame.image != null) {
                                        matImage = openCVFrameConverter.convert(frame);

                                        if (matImage != null) {
                                            try {
                                                if (webcamImageColor_ >= 0) {
                                                    opencv_imgproc.cvtColor(matImage, matImage, webcamImageColor_);
                                                }

                                                boolean needView = webcamStage_.isShowing() && !isUpdateWebcamView_;
                                                boolean needResultImage = (funcResultImage_ != null)
                                                        && !isUpdateFuncResultImage_;
                                                boolean needSnapshot = (snapshotRequest_.get() != null);
                                                if (needView || needResultImage || needSnapshot) {
                                                    bufferedImage = Java2DFrameUtils.toBufferedImage(matImage);

                                                    if (bufferedImage != null) {
                                                        try {
                                                            if (needView) {
                                                                webcamImage.set(
                                                                        SwingFXUtils.toFXImage(bufferedImage,
                                                                                webcamImage.get()));
                                                                isUpdateWebcamView_ = true;

                                                                Platform.runLater(() -> {
                                                                    imageProperty_.set(webcamImage.get());
                                                                    isUpdateWebcamView_ = false;
                                                                });
                                                            }

                                                            String imageType = effectiveImageType();
                                                            if (needResultImage || needSnapshot) {
                                                                ImageIO.write(bufferedImage, imageType, byteArrayImage);

                                                                if (byteArrayImage.size() > 0) {
                                                                    byte[] encoded = byteArrayImage.toByteArray();
                                                                    String base64 = Base64.getEncoder()
                                                                            .encodeToString(encoded);
                                                                    resultImageBase64_ = base64;

                                                                    CompletableFuture<String> snap = snapshotRequest_
                                                                            .getAndSet(null);
                                                                    if (snap != null) {
                                                                        snap.complete(base64);
                                                                    }

                                                                    if (needResultImage) {
                                                                        resultImage_ = gson_.toJson(encoded);
                                                                        isUpdateFuncResultImage_ = true;

                                                                        Platform.runLater(() -> {
                                                                            String func = funcResultImage_;
                                                                            WebEngine engine = webEngine_;
                                                                            String image = resultImage_;
                                                                            if ((func != null) && (engine != null)
                                                                                    && (image != null)) {
                                                                                if (state_ == Worker.State.SUCCEEDED) {
                                                                                    try {
                                                                                        engine.executeScript(
                                                                                                func + "('"
                                                                                                        + image + "', "
                                                                                                        + ScriptValue
                                                                                                                .literal(
                                                                                                                        imageType)
                                                                                                        + ");");
                                                                                    } catch (JSException ex) {
                                                                                        writeStackTrace(FUNCTION_NAME,
                                                                                                ex);
                                                                                    }
                                                                                }
                                                                            }
                                                                            isUpdateFuncResultImage_ = false;
                                                                        });
                                                                    }
                                                                }
                                                                byteArrayImage.flush();
                                                                byteArrayImage.reset();
                                                            }
                                                        } finally {
                                                            bufferedImage.flush();
                                                        }
                                                    }
                                                }

                                                BlobInfo blobInfo = blobInfo_;
                                                if ((funcResultBlob_ != null) && !isUpdateFuncResultBlob_
                                                        && (blobInfo != null) && (blobInfo.size != null)
                                                        && (blobInfo.mean != null)) {
                                                    matBlob = opencv_dnn.blobFromImage(matImage, blobInfo.scalefactor,
                                                            blobInfo.size, blobInfo.mean, blobInfo.swapRB,
                                                            blobInfo.crop, blobInfo.ddepth);
                                                    if (matBlob != null) {
                                                        try {
                                                            bytesBlob = new byte[(int) (matBlob.total()
                                                                    * matBlob.elemSize())];
                                                            matBlob.data().get(bytesBlob);
                                                        } finally {
                                                            matBlob.release();
                                                        }
                                                        resultBlob_ = gson_.toJson(bytesBlob);
                                                        isUpdateFuncResultBlob_ = true;

                                                        Platform.runLater(() -> {
                                                            String func = funcResultBlob_;
                                                            WebEngine engine = webEngine_;
                                                            String blob = resultBlob_;
                                                            if ((func != null) && (engine != null) && (blob != null)) {
                                                                if (state_ == Worker.State.SUCCEEDED) {
                                                                    try {
                                                                        engine.executeScript(
                                                                                func + "('" + blob + "');");
                                                                    } catch (JSException ex) {
                                                                        writeStackTrace(FUNCTION_NAME, ex);
                                                                    }
                                                                }
                                                            }
                                                            isUpdateFuncResultBlob_ = false;
                                                        });
                                                    }
                                                }

                                                String savePath = saveFilePath_;
                                                if (savePath != null) {
                                                    opencv_imgcodecs.imwrite(savePath, matImage);
                                                    saveFilePath_ = null;
                                                }
                                            } finally {
                                                matImage.release();
                                            }
                                        }
                                    }
                                }
                            } catch (IOException ex) {
                                writeStackTrace(FUNCTION_NAME, ex);
                            } finally {
                                byteArrayImage.reset();
                            }
                        } else {
                            try {
                                TimeUnit.MILLISECONDS.sleep(1L);
                            } catch (InterruptedException ex) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                }
                return null;
            }
        };
    }

    /**
     *
     * @param webViewer
     */
    @Override
    public void initialize(WebViewerPlugin webViewer) {
        super.initialize(webViewer);
        webEngine_ = webEngine();
    }

    /**
     *
     * @return
     */
    @Override
    public String functionName() {
        return FUNCTION_NAME;
    }

    /**
     *
     * @param state
     */
    @Override
    public void state(State state) {
        state_ = state;
    }

    /**
     * wait the end of the capture tasks of the camera
     *
     * @param millis maximum time to wait in milliseconds
     * @return true when no task runs anymore, false when the time is out
     */
    public boolean awaitTermination(int millis) {
        long deadline = System.currentTimeMillis() + millis;

        while (runningTasks_.get() != 0) {
            if (System.currentTimeMillis() >= deadline) {
                return false;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(10L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    /**
     *
     */
    @Override
    public void close() {
        // the capture loop ends when isPlay_ is cleared : it is cleared and the task is cancelled
        // before the wait, so close() can free the camera when no task reads it anymore
        isPlay_ = false;
        cancelSnapshotRequest();
        cancel();

        // the camera is freed only when no capture task reads it anymore : a task which never
        // ends keeps the device open, so the task does not read a freed grabber
        if (!awaitTermination(TASK_TERMINATION_MILLIS)) {
            write(FUNCTION_NAME, "WebCam is still running : the webcam is not closed", true);
            return;
        }
        if (webcam_ != null) {
            try {
                webcam_.close();
            } catch (FrameGrabber.Exception ex) {
                writeStackTrace(FUNCTION_NAME, ex);
            }
            webcam_ = null;
        }
        webcamStage_.close();
        resultImage_ = null;
        resultImageBase64_ = null;
        resultBlob_ = null;
    }

}
