/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ken.mizoguch.webviewer.webcam;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.concurrent.Service;
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
import netscape.javascript.JSException;
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

/**
 *
 * @author mizoguch_ken
 */
public class WebCam extends Service<Void> implements WebViewerPlugin {

    private WebViewerPlugin webViewer_;
    private static final String FUNCTION_NAME = "webcam";

    private WebEngine webEngine_;
    private Worker.State state_;

    private class WebcamInfo {

        int index;
        String name;
    }

    private class BlobInfo {

        double scalefactor;
        Size size;
        Scalar mean;
        boolean swapRB;
        boolean crop;
        int ddepth;
    }

    private OpenCVFrameGrabber webcam_;

    private final Stage webcamStage_;
    private final BorderPane webcamPane_;
    private final ImageView webcamImageView_;
    private final ObjectProperty<Image> imageProperty_;

    private String funcResultImage_;
    private String funcResultBlob_;
    private String webcamImageType_;
    private Size webcamImageSize_;
    private int webcamImageColor_;
    private String resultImage_;
    private String resultBlob_;
    private BlobInfo blobInfo_;

    private boolean isPlay_;
    private boolean isUpdateWebcamView_;
    private boolean isUpdateFuncResultImage_;
    private boolean isUpdateFuncResultBlob_;
    private String saveFilePath_;

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
        webcamImageType_ = "bmp";

        blobInfo_ = null;
        funcResultImage_ = null;
        funcResultBlob_ = null;
        resultImage_ = null;
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
        funcResultImage_ = func;
    }

    /**
     *
     * @param func
     * @param blobInfo
     */
    public void setNotifyResultBlob(String func, String blobInfo) {
        funcResultBlob_ = func;

        if (blobInfo_ == null) {
            blobInfo_ = new BlobInfo();
        }

        JsonObject info = gson_.fromJson(blobInfo, JsonObject.class);
        if (info.has("scalefactor")) {
            blobInfo_.scalefactor = info.getAsJsonPrimitive("scalefactor").getAsDouble();
        }

        if (info.has("size")) {
            JsonObject size = info.getAsJsonObject("size");

            if ((size.has("width")) && size.has("height")) {
                if (blobInfo_.size == null) {
                    blobInfo_.size = new Size();
                }

                blobInfo_.size.width(size.getAsJsonPrimitive("width").getAsInt());
                blobInfo_.size.height(size.getAsJsonPrimitive("height").getAsInt());
            }
        }

        if (info.has("mean")) {
            JsonArray mean = info.getAsJsonArray("mean");
            switch (mean.size()) {
                case 1:
                    blobInfo_.mean = new Scalar(mean.get(0).getAsDouble());
                    break;
                case 2:
                    blobInfo_.mean = new Scalar(mean.get(0).getAsDouble(), mean.get(1).getAsDouble());
                    break;
                case 4:
                    blobInfo_.mean = new Scalar(mean.get(0).getAsDouble(), mean.get(1).getAsDouble(), mean.get(2).getAsDouble(), mean.get(3).getAsDouble());
                    break;
                default:
                    break;
            }
        }

        if (info.has("swapRB")) {
            blobInfo_.swapRB = info.getAsJsonPrimitive("swapRB").getAsBoolean();
        }

        if (info.has("crop")) {
            blobInfo_.crop = info.getAsJsonPrimitive("crop").getAsBoolean();
        }

        if (info.has("ddepth")) {
            switch (info.getAsJsonPrimitive("ddepth").getAsString()) {
                case "CV_8U":
                    blobInfo_.ddepth = opencv_core.CV_8U;
                    break;
                case "CV_8S":
                    blobInfo_.ddepth = opencv_core.CV_8S;
                    break;
                case "CV_16U":
                    blobInfo_.ddepth = opencv_core.CV_16U;
                    break;
                case "CV_16S":
                    blobInfo_.ddepth = opencv_core.CV_16S;
                    break;
                case "CV_32S":
                    blobInfo_.ddepth = opencv_core.CV_32S;
                    break;
                case "CV_32F":
                    blobInfo_.ddepth = opencv_core.CV_32F;
                    break;
                case "CV_64F":
                    blobInfo_.ddepth = opencv_core.CV_64F;
                    break;
                case "CV_16F":
                    blobInfo_.ddepth = opencv_core.CV_16F;
                    break;
            }
        }
    }

    /**
     *
     * @return
     */
    public String getWebcams() {
        int idx = videoInput.listDevices();
        List<WebcamInfo> webcamInfos = new ArrayList<>();

        for (int i = 0; i < idx; i++) {
            WebcamInfo webcamInfo = new WebcamInfo();
            webcamInfo.index = i;
            webcamInfo.name = videoInput.getDeviceName(i).getString();
            webcamInfos.add(webcamInfo);
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
            type = type.trim().toLowerCase(Locale.getDefault());
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
        if (webcamImageSize_ != null) {
            return gson_.toJson(webcamImageSize_);
        }
        return null;
    }

    /**
     *
     * @param width
     * @param height
     */
    public void setImageSize(int width, int height) {
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
    public int getImageColor() {
        return webcamImageColor_;
    }

    /**
     *
     * @param color
     */
    public void setImageColor(String color) {
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
        }
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
            webcam_.start();

            if (webcamImageSize_ == null) {
                webcamImageSize_ = new Size();
            }

            if ((webcamImageSize_.width() <= 0) || (webcamImageSize_.height() <= 0)) {
                setImageSize(webcam_.getImageWidth(), webcam_.getImageHeight());
            }

            isPlay_ = false;
            return true;
        } else {
            webViewer_.write(FUNCTION_NAME, "Camera device is already open", true);
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
        webViewer_.write(FUNCTION_NAME, "Camera device is not open", true);
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
            if (!this.isRunning()) {
                this.reset();
                this.start();
            }
            return true;
        }
        webViewer_.write(FUNCTION_NAME, "Camera device is not open", true);
        return false;
    }

    /**
     *
     * @return
     */
    public Boolean stop() {
        isPlay_ = false;
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
            if (isPlay_) {
                if (saveFilePath_ == null) {
                    saveFilePath_ = path;
                    return true;
                }
            } else {
                Frame frame = webcam_.grab();

                if (frame != null) {
                    if (frame.image != null) {
                        OpenCVFrameConverter.ToMat openCVFrameConverter = new OpenCVFrameConverter.ToMat();
                        Mat matImage = matImage = openCVFrameConverter.convert(frame);

                        if (matImage != null) {
                            if (webcamImageColor_ >= 0) {
                                opencv_imgproc.cvtColor(matImage, matImage, webcamImageColor_);
                            }
                            opencv_imgcodecs.imwrite(path, matImage);
                            matImage.release();

                            return true;
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
     */
    public Boolean isShowing() {
        if (webcam_ != null) {
            return webcamStage_.isShowing();
        }
        webViewer_.write(FUNCTION_NAME, "Camera device is not open", true);
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
        webViewer_.write(FUNCTION_NAME, "Camera device is not open", true);
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
        webViewer_.write(FUNCTION_NAME, "Camera device is not open", true);
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
        webViewer_.write(FUNCTION_NAME, "Camera device is not open", true);
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
        webViewer_.write(FUNCTION_NAME, "Camera device is not open", true);
        return null;
    }

    /**
     *
     * @return
     */
    public Boolean isFocused() {
        if (webcam_ != null) {
            webcamStage_.isFocused();
            return true;
        }
        webViewer_.write(FUNCTION_NAME, "Camera device is not open", true);
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
        webViewer_.write(FUNCTION_NAME, "Camera device is not open", true);
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
        webViewer_.write(FUNCTION_NAME, "Camera device is not open", true);
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
        webViewer_.write(FUNCTION_NAME, "Camera device is not open", true);
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
        webViewer_.write(FUNCTION_NAME, "Camera device is not open", true);
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
        webViewer_.write(FUNCTION_NAME, "Camera device is not open", true);
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
        webViewer_.write(FUNCTION_NAME, "Camera device is not open", true);
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
        webViewer_.write(FUNCTION_NAME, "Camera device is not open", true);
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
        webViewer_.write(FUNCTION_NAME, "Camera device is not open", true);
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
        webViewer_.write(FUNCTION_NAME, "Camera device is not open", true);
        return false;
    }

    @Override
    protected Task<Void> createTask() {
        return new Task<Void>() {
            @Override
            protected Void call() {
                Frame frame;
                OpenCVFrameConverter.ToMat openCVFrameConverter = new OpenCVFrameConverter.ToMat();
                Mat matImage, matBlob;
                AtomicReference<WritableImage> webcamImage = new AtomicReference<>();
                BufferedImage bufferedImage;
                ByteArrayOutputStream byteArrayImage = new ByteArrayOutputStream();
                byte[] bytesBlob;

                while (isPlay_ && (webcam_ != null)) {
                    if ((webcamStage_.isShowing() && !isUpdateWebcamView_)
                            || ((funcResultImage_ != null) && !isUpdateFuncResultImage_)
                            || ((funcResultBlob_ != null) && !isUpdateFuncResultBlob_)
                            || (saveFilePath_ != null)) {
                        try {
                            frame = webcam_.grab();

                            if (frame != null) {
                                if (frame.image != null) {
                                    matImage = openCVFrameConverter.convert(frame);

                                    if (matImage != null) {
                                        if (webcamImageColor_ >= 0) {
                                            opencv_imgproc.cvtColor(matImage, matImage, webcamImageColor_);
                                        }

                                        if ((webcamStage_.isShowing() && !isUpdateWebcamView_)
                                                || ((funcResultImage_ != null) && !isUpdateFuncResultImage_)) {
                                            bufferedImage = Java2DFrameUtils.toBufferedImage(matImage);

                                            if (bufferedImage != null) {
                                                if (webcamStage_.isShowing() && !isUpdateWebcamView_) {
                                                    webcamImage.set(SwingFXUtils.toFXImage(bufferedImage, webcamImage.get()));
                                                    isUpdateWebcamView_ = true;

                                                    Platform.runLater(() -> {
                                                        imageProperty_.set(webcamImage.get());
                                                        isUpdateWebcamView_ = false;
                                                    });
                                                }

                                                if ((funcResultImage_ != null) && (!isUpdateFuncResultImage_)) {
                                                    ImageIO.write(bufferedImage, webcamImageType_, byteArrayImage);

                                                    if (byteArrayImage.size() > 0) {
                                                        resultImage_ = gson_.toJson(byteArrayImage.toByteArray());
                                                        isUpdateFuncResultImage_ = true;

                                                        Platform.runLater(() -> {
                                                            if (funcResultImage_ != null) {
                                                                if (state_ == Worker.State.SUCCEEDED) {
                                                                    try {
                                                                        webEngine_.executeScript(funcResultImage_ + "('" + resultImage_ + "', '" + webcamImageType_ + "');");
                                                                    } catch (JSException ex) {
                                                                        webViewer_.writeStackTrace(FUNCTION_NAME, ex);
                                                                    }
                                                                }
                                                            }
                                                            isUpdateFuncResultImage_ = false;
                                                        });
                                                    }
                                                    byteArrayImage.flush();
                                                    byteArrayImage.reset();
                                                }
                                                bufferedImage.flush();
                                            }
                                        }

                                        if ((funcResultBlob_ != null) && !isUpdateFuncResultBlob_) {
                                            matBlob = opencv_dnn.blobFromImage(matImage, blobInfo_.scalefactor, blobInfo_.size, blobInfo_.mean, blobInfo_.swapRB, blobInfo_.crop, blobInfo_.ddepth);
                                            bytesBlob = new byte[(int) (matBlob.total() * matBlob.elemSize())];
                                            matBlob.data().get(bytesBlob);
                                            matBlob.release();
                                            resultBlob_ = gson_.toJson(bytesBlob);
                                            isUpdateFuncResultBlob_ = true;

                                            Platform.runLater(() -> {
                                                if (resultBlob_ != null) {
                                                    if (state_ == Worker.State.SUCCEEDED) {
                                                        try {
                                                            webEngine_.executeScript(funcResultBlob_ + "('" + resultBlob_ + "');");
                                                        } catch (JSException ex) {
                                                            webViewer_.writeStackTrace(FUNCTION_NAME, ex);
                                                        }
                                                    }
                                                }
                                                isUpdateFuncResultBlob_ = false;
                                            });
                                        }

                                        if (saveFilePath_ != null) {
                                            opencv_imgcodecs.imwrite(saveFilePath_, matImage);
                                            saveFilePath_ = null;
                                        }
                                        matImage.release();
                                    }
                                }
                            }
                        } catch (FrameGrabber.Exception ex) {
                            webViewer_.writeStackTrace(FUNCTION_NAME, ex);
                        } catch (IOException ex) {
                            webViewer_.writeStackTrace(FUNCTION_NAME, ex);
                        } finally {
                            try {
                                byteArrayImage.close();
                            } catch (IOException ex) {
                                webViewer_.writeStackTrace(FUNCTION_NAME, ex);
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
        webViewer_ = webViewer;
        webEngine_ = webViewer_.webEngine();
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
     *
     */
    @Override
    public void close() {
        if (webcam_ != null) {
            try {
                webcam_.close();
            } catch (FrameGrabber.Exception ex) {
                webViewer_.writeStackTrace(FUNCTION_NAME, ex);
            }
            webcam_ = null;
        }
        webcamStage_.close();
        resultImage_ = null;
        resultBlob_ = null;
        this.cancel();
    }

    /**
     *
     * @return
     */
    @Override
    public Stage stage() {
        return webViewer_.stage();
    }

    /**
     *
     * @return
     */
    @Override
    public List<Image> icons() {
        return webViewer_.icons();
    }

    /**
     *
     * @return
     */
    @Override
    public WebEngine webEngine() {
        return webViewer_.webEngine();
    }

    /**
     *
     * @return
     */
    @Override
    public Path webPath() {
        return webViewer_.webPath();
    }

    /**
     *
     * @param name
     * @param throwable
     */
    @Override
    public void writeStackTrace(String name, Throwable throwable) {
        webViewer_.writeStackTrace(name, throwable);
    }

    /**
     *
     * @param name
     * @param msg
     * @param err
     */
    @Override
    public void write(String name, String msg, boolean err) {
        webViewer_.write(name, msg, err);
    }
}
