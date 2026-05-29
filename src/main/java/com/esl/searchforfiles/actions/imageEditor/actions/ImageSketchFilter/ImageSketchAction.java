package com.esl.searchforfiles.actions.imageEditor.actions.ImageSketchFilter;

import com.esl.searchforfiles.actions.imageEditor.ImageEditAction;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Point;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_core.bitwise_not;
import static org.bytedeco.opencv.global.opencv_imgproc.dilate;
import static org.bytedeco.opencv.global.opencv_imgproc.morphologyDefaultBorderValue;

public class ImageSketchAction extends ImageEditAction {

    // ── Parâmetros (valores default) ──────────────────────────────
    private int kernelSize      = 5;   // tamanho do kernel (ímpares: 1, 3, 5, 7, 9...)
    private int dilateIterations = 1;  // número de iterações da dilatação

    // O efeito só é aplicado após o usuário clicar em "Aplicar"
    private boolean effectApplied = false;

    public ImageSketchAction() {
        super("Filtro de desenho");
        syncParams();
    }

    // ── Getters / Setters ─────────────────────────────────────────
    public int  getKernelSize()              { return kernelSize; }
    public void setKernelSize(int v)         { kernelSize = Math.max(1, v | 1); syncParams(); } // força ímpar

    public int  getDilateIterations()        { return dilateIterations; }
    public void setDilateIterations(int v)   { dilateIterations = Math.max(1, v); syncParams(); }

    public boolean isEffectApplied()         { return effectApplied; }
    public void setEffectApplied(boolean v)  { effectApplied = v; syncParams(); }


    public boolean hasEffect() { return effectApplied; }

    private void syncParams() {
        setParam("kernel",     kernelSize + "×" + kernelSize);
        setParam("iterações",  String.valueOf(dilateIterations));
        setParam("efeito",     effectApplied ? "ativo" : "inativo");
    }

    // ── Aplicação ─────────────────────────────────────────────────

    public BufferedImage apply(BufferedImage img) {
        if (!isEnabled() || img == null || !hasEffect()) return img;

        Mat mat    = bufferedImageToMat(img);
        Mat kernel = Mat.ones(kernelSize, kernelSize, CV_8U).asMat();

        Mat imgGray = new Mat();
        opencv_imgproc.cvtColor(mat, imgGray, opencv_imgproc.COLOR_BGR2GRAY);

        Mat imgDilated = new Mat();
        dilate(imgGray, imgDilated, kernel,
                new Point(-1, -1), dilateIterations,
                BORDER_CONSTANT, morphologyDefaultBorderValue());

        Mat imgDiff = new Mat();
        absdiff(imgDilated, imgGray, imgDiff);

        Mat imgInverted = new Mat();
        bitwise_not(imgDiff, imgInverted);

        return matToBufferedImage(imgInverted);
    }

    // ── Conversões ────────────────────────────────────────────────
    private static Mat bufferedImageToMat(BufferedImage bi) {
        BufferedImage bgrImg = new BufferedImage(
                bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        bgrImg.getGraphics().drawImage(bi, 0, 0, null);
        byte[] data = ((DataBufferByte) bgrImg.getRaster().getDataBuffer()).getData();
        Mat mat = new Mat(bgrImg.getHeight(), bgrImg.getWidth(), opencv_core.CV_8UC3);
        mat.data().put(data);
        return mat;
    }

    public static BufferedImage matToBufferedImage(Mat mat) {
        Mat converted = new Mat();
        if (mat.channels() == 1)
            opencv_imgproc.cvtColor(mat, converted, opencv_imgproc.COLOR_GRAY2BGR);
        else
            converted = mat;

        int w = converted.cols(), h = converted.rows(), ch = converted.channels();
        byte[] source = new byte[w * h * ch];
        converted.data().get(source);

        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
        byte[] target = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(source, 0, target, 0, source.length);
        return image;
    }
}
