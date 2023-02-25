/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/module-info.java to edit this template
 */

module WebCam {
    requires jdk.jsobject;
    requires java.desktop;
    requires javafx.web;
    requires javafx.swing;
    
    requires org.bytedeco.javacv.platform;
    requires org.bytedeco.javacv;
    requires org.bytedeco.javacpp;
    requires org.bytedeco.openblas;
    requires org.bytedeco.opencv;
    requires org.bytedeco.ffmpeg;
    requires org.bytedeco.flycapture;
    requires org.bytedeco.libdc1394;
    requires org.bytedeco.libfreenect;
    requires org.bytedeco.libfreenect2;
    requires org.bytedeco.librealsense;
    requires org.bytedeco.librealsense2;
    requires org.bytedeco.videoinput;
    requires org.bytedeco.artoolkitplus;
    requires org.bytedeco.flandmark;
    requires org.bytedeco.leptonica;
    requires org.bytedeco.tesseract;
    requires javafx.graphics;
    requires javafx.base;
    requires org.bytedeco.openblas.platform;
    requires org.bytedeco.javacpp.platform;
    requires org.bytedeco.javacpp.android.arm;
    requires org.bytedeco.javacpp.android.arm64;
    requires org.bytedeco.javacpp.android.x86;
    requires org.bytedeco.javacpp.android.x86_64;
    requires org.bytedeco.javacpp.ios.arm64;
    requires org.bytedeco.javacpp.ios.x86_64;
    requires org.bytedeco.javacpp.linux.armhf;
    requires org.bytedeco.javacpp.linux.arm64;
    requires org.bytedeco.javacpp.linux.ppc64le;
    requires org.bytedeco.javacpp.linux.x86;
    requires org.bytedeco.javacpp.linux.x86_64;
    requires org.bytedeco.javacpp.macosx.arm64;
    requires org.bytedeco.javacpp.macosx.x86_64;
    requires org.bytedeco.javacpp.windows.x86;
    requires org.bytedeco.javacpp.windows.x86_64;
    requires org.bytedeco.openblas.android.arm;
    requires org.bytedeco.openblas.android.arm64;
    requires org.bytedeco.openblas.android.x86;
    requires org.bytedeco.openblas.android.x86_64;
    requires org.bytedeco.openblas.ios.arm64;
    requires org.bytedeco.openblas.ios.x86_64;
    requires org.bytedeco.openblas.linux.x86;
    requires org.bytedeco.openblas.linux.x86_64;
    requires org.bytedeco.openblas.linux.armhf;
    requires org.bytedeco.openblas.linux.arm64;
    requires org.bytedeco.openblas.linux.ppc64le;
    requires org.bytedeco.openblas.macosx.arm64;
    requires org.bytedeco.openblas.macosx.x86_64;
    requires org.bytedeco.openblas.windows.x86;
    requires org.bytedeco.openblas.windows.x86_64;
    requires org.bytedeco.opencv.platform;
    requires org.bytedeco.opencv.android.arm;
    requires org.bytedeco.opencv.android.arm64;
    requires org.bytedeco.opencv.android.x86;
    requires org.bytedeco.opencv.android.x86_64;
    requires org.bytedeco.opencv.ios.arm64;
    requires org.bytedeco.opencv.ios.x86_64;
    requires org.bytedeco.opencv.linux.x86;
    requires org.bytedeco.opencv.linux.x86_64;
    requires org.bytedeco.opencv.linux.armhf;
    requires org.bytedeco.opencv.linux.arm64;
    requires org.bytedeco.opencv.linux.ppc64le;
    requires org.bytedeco.opencv.macosx.arm64;
    requires org.bytedeco.opencv.macosx.x86_64;
    requires org.bytedeco.opencv.windows.x86;
    requires org.bytedeco.opencv.windows.x86_64;
    requires org.bytedeco.ffmpeg.platform;
    requires org.bytedeco.ffmpeg.android.arm;
    requires org.bytedeco.ffmpeg.android.arm64;
    requires org.bytedeco.ffmpeg.android.x86;
    requires org.bytedeco.ffmpeg.android.x86_64;
    requires org.bytedeco.ffmpeg.linux.x86;
    requires org.bytedeco.ffmpeg.linux.x86_64;
    requires org.bytedeco.ffmpeg.linux.armhf;
    requires org.bytedeco.ffmpeg.linux.arm64;
    requires org.bytedeco.ffmpeg.linux.ppc64le;
    requires org.bytedeco.ffmpeg.macosx.arm64;
    requires org.bytedeco.ffmpeg.macosx.x86_64;
    requires org.bytedeco.ffmpeg.windows.x86;
    requires org.bytedeco.ffmpeg.windows.x86_64;
    requires org.bytedeco.flycapture.platform;
    requires org.bytedeco.flycapture.linux.x86;
    requires org.bytedeco.flycapture.linux.x86_64;
    requires org.bytedeco.flycapture.linux.armhf;
    requires org.bytedeco.flycapture.linux.arm64;
    requires org.bytedeco.flycapture.windows.x86;
    requires org.bytedeco.flycapture.windows.x86_64;
    requires org.bytedeco.libdc1394.platform;
    requires org.bytedeco.libdc1394.linux.x86;
    requires org.bytedeco.libdc1394.linux.x86_64;
    requires org.bytedeco.libdc1394.linux.armhf;
    requires org.bytedeco.libdc1394.linux.arm64;
    requires org.bytedeco.libdc1394.linux.ppc64le;
    requires org.bytedeco.libdc1394.macosx.x86_64;
    requires org.bytedeco.libdc1394.windows.x86;
    requires org.bytedeco.libdc1394.windows.x86_64;
    requires org.bytedeco.libfreenect.platform;
    requires org.bytedeco.libfreenect.linux.x86;
    requires org.bytedeco.libfreenect.linux.x86_64;
    requires org.bytedeco.libfreenect.linux.armhf;
    requires org.bytedeco.libfreenect.linux.arm64;
    requires org.bytedeco.libfreenect.linux.ppc64le;
    requires org.bytedeco.libfreenect.macosx.x86_64;
    requires org.bytedeco.libfreenect.windows.x86;
    requires org.bytedeco.libfreenect.windows.x86_64;
    requires org.bytedeco.libfreenect2.platform;
    requires org.bytedeco.libfreenect2.linux.x86;
    requires org.bytedeco.libfreenect2.linux.x86_64;
    requires org.bytedeco.libfreenect2.macosx.x86_64;
    requires org.bytedeco.libfreenect2.windows.x86_64;
    requires org.bytedeco.librealsense.platform;
    requires org.bytedeco.librealsense.linux.armhf;
    requires org.bytedeco.librealsense.linux.arm64;
    requires org.bytedeco.librealsense.linux.x86;
    requires org.bytedeco.librealsense.linux.x86_64;
    requires org.bytedeco.librealsense.macosx.x86_64;
    requires org.bytedeco.librealsense.windows.x86;
    requires org.bytedeco.librealsense.windows.x86_64;
    requires org.bytedeco.librealsense2.platform;
    requires org.bytedeco.librealsense2.linux.armhf;
    requires org.bytedeco.librealsense2.linux.arm64;
    requires org.bytedeco.librealsense2.linux.x86;
    requires org.bytedeco.librealsense2.linux.x86_64;
    requires org.bytedeco.librealsense2.macosx.x86_64;
    requires org.bytedeco.librealsense2.windows.x86;
    requires org.bytedeco.librealsense2.windows.x86_64;
    requires org.bytedeco.videoinput.platform;
    requires org.bytedeco.videoinput.windows.x86;
    requires org.bytedeco.videoinput.windows.x86_64;
    requires org.bytedeco.artoolkitplus.platform;
    requires org.bytedeco.artoolkitplus.android.arm;
    requires org.bytedeco.artoolkitplus.android.arm64;
    requires org.bytedeco.artoolkitplus.android.x86;
    requires org.bytedeco.artoolkitplus.android.x86_64;
    requires org.bytedeco.artoolkitplus.linux.x86;
    requires org.bytedeco.artoolkitplus.linux.x86_64;
    requires org.bytedeco.artoolkitplus.linux.armhf;
    requires org.bytedeco.artoolkitplus.linux.arm64;
    requires org.bytedeco.artoolkitplus.linux.ppc64le;
    requires org.bytedeco.artoolkitplus.macosx.x86_64;
    requires org.bytedeco.artoolkitplus.windows.x86;
    requires org.bytedeco.artoolkitplus.windows.x86_64;
    requires org.bytedeco.flandmark.platform;
    requires org.bytedeco.flandmark.android.arm;
    requires org.bytedeco.flandmark.android.arm64;
    requires org.bytedeco.flandmark.android.x86;
    requires org.bytedeco.flandmark.android.x86_64;
    requires org.bytedeco.flandmark.linux.x86;
    requires org.bytedeco.flandmark.linux.x86_64;
    requires org.bytedeco.flandmark.linux.armhf;
    requires org.bytedeco.flandmark.linux.arm64;
    requires org.bytedeco.flandmark.linux.ppc64le;
    requires org.bytedeco.flandmark.macosx.x86_64;
    requires org.bytedeco.flandmark.windows.x86;
    requires org.bytedeco.flandmark.windows.x86_64;
    requires org.bytedeco.leptonica.platform;
    requires org.bytedeco.leptonica.android.arm;
    requires org.bytedeco.leptonica.android.arm64;
    requires org.bytedeco.leptonica.android.x86;
    requires org.bytedeco.leptonica.android.x86_64;
    requires org.bytedeco.leptonica.linux.x86;
    requires org.bytedeco.leptonica.linux.x86_64;
    requires org.bytedeco.leptonica.linux.armhf;
    requires org.bytedeco.leptonica.linux.arm64;
    requires org.bytedeco.leptonica.linux.ppc64le;
    requires org.bytedeco.leptonica.macosx.arm64;
    requires org.bytedeco.leptonica.macosx.x86_64;
    requires org.bytedeco.leptonica.windows.x86;
    requires org.bytedeco.leptonica.windows.x86_64;
    requires org.bytedeco.tesseract.platform;
    requires org.bytedeco.tesseract.android.arm;
    requires org.bytedeco.tesseract.android.arm64;
    requires org.bytedeco.tesseract.android.x86;
    requires org.bytedeco.tesseract.android.x86_64;
    requires org.bytedeco.tesseract.linux.x86;
    requires org.bytedeco.tesseract.linux.x86_64;
    requires org.bytedeco.tesseract.linux.armhf;
    requires org.bytedeco.tesseract.linux.arm64;
    requires org.bytedeco.tesseract.linux.ppc64le;
    requires org.bytedeco.tesseract.macosx.arm64;
    requires org.bytedeco.tesseract.macosx.x86_64;
    requires org.bytedeco.tesseract.windows.x86;
    requires org.bytedeco.tesseract.windows.x86_64;
    requires com.google.gson;
}
