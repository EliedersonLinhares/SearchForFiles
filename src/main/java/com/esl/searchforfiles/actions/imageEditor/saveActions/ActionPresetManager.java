package com.esl.searchforfiles.actions.imageEditor.saveActions;


import com.esl.searchforfiles.actions.imageEditor.ActionCardPanel;
import com.esl.searchforfiles.actions.imageEditor.ImageEditAction;
import com.esl.searchforfiles.actions.imageEditor.actions.ImageAdjust.ImageAdjustAction;
import com.esl.searchforfiles.actions.imageEditor.actions.ImageBlurBrush.ImageBlurBrushAction;
import com.esl.searchforfiles.actions.imageEditor.actions.ImageCrop.ImageCropAction;
import com.esl.searchforfiles.actions.imageEditor.actions.ImagePaintBrush.ImagePaintBrushAction;
import com.esl.searchforfiles.actions.imageEditor.actions.ImageResize.ImageResizeAction;
import com.esl.searchforfiles.actions.imageEditor.actions.ImageRotate.ImageRotateAction;
import com.esl.searchforfiles.actions.imageEditor.actions.ImageSketchFilter.ImageSketchAction;
import jnafilechooser.api.JnaFileChooser;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializa e desserializa a lista de actions ativas para/de um arquivo JSON
 * simples, sem dependências externas — usa apenas java.util e java.io.
 *
 * Formato do arquivo:
 * [
 *   { "type": "adjust", "enabled": true, "brightness": 0.10, "contrast": 1.20, ... },
 *   { "type": "rotate", "enabled": true, "transform": "ROTATE_CW" },
 *   { "type": "resize", "enabled": true, "mode": "PERCENTAGE", "percentage": 75.0 },
 *   { "type": "crop",   "enabled": true, "aspect": "RATIO_16_9",
 *                        "normX": 0.0, "normY": 0.0, "normW": 0.8, "normH": 0.8 },
 *   { "type": "sketch", "enabled": true, "kernelSize": 5, "dilateIterations": 2 }
 * ]
 */
public class ActionPresetManager {

    private static final String EXT         = ".imgpreset";
    private static final String EXT_DESC    = "Image Preset (*" + EXT + ")";
    private static File file;

    public File getFile() {
        return file;
    }



    // ══════════════════════════════════════════════════════════════
    // SALVAR
    // ══════════════════════════════════════════════════════════════

    /**
     * Abre um JFileChooser para o usuário escolher onde salvar,
     * serializa as actions e grava o arquivo.
     *
     * @param parent      janela pai para o diálogo
     * @param actionCards lista atual de cards (em ordem)
     */
    public static void savePreset(Window parent, List<ActionCardPanel> actionCards) {
        if (actionCards.isEmpty()) {
            JOptionPane.showMessageDialog(parent,
                    "Nenhuma ação ativa para salvar.",
                    "Salvar preset", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JnaFileChooser fa = new JnaFileChooser();
        fa.setTitle("Salvar preset de ações");
        fa.addFilter("Arquivo de açes (*.imgpreset)", "imgpreset");
        if (fa.showSaveDialog(parent)) {
            File file = ensureExtension(fa.getSelectedFile());
            String json = serialize(actionCards);

            try (BufferedWriter w = new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8))) {
                w.write(json);
                JOptionPane.showMessageDialog(parent,
                        "Preset salvo em:\n" + file.getAbsolutePath(),
                        "Salvo", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(parent,
                        "Erro ao salvar: " + e.getMessage(),
                        "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }

//        JFileChooser fc = buildChooser();
//        fc.setDialogTitle("Salvar preset de ações");
//        if (fc.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return;
//
//        File file = ensureExtension(fc.getSelectedFile());
//        String json = serialize(actionCards);
//
//        try (BufferedWriter w = new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8))) {
//            w.write(json);
//            JOptionPane.showMessageDialog(parent,
//                    "Preset salvo em:\n" + file.getAbsolutePath(),
//                    "Salvo", JOptionPane.INFORMATION_MESSAGE);
//        } catch (IOException e) {
//            JOptionPane.showMessageDialog(parent,
//                    "Erro ao salvar: " + e.getMessage(),
//                    "Erro", JOptionPane.ERROR_MESSAGE);
//        }
    }

    // ══════════════════════════════════════════════════════════════
    // CARREGAR
    // ══════════════════════════════════════════════════════════════

    /**
     * Abre um JFileChooser, lê o arquivo e retorna as actions reconstituídas
     * na ordem original. Retorna null se o usuário cancelar ou ocorrer erro.
     *
     * @param parent janela pai para o diálogo e mensagens de erro
     */
    public static List<ImageEditAction> loadPreset(Window parent) {

        JnaFileChooser fa = new JnaFileChooser();
       fa.setTitle("Carregar preset de ações");
       fa.addFilter("Arquivo de Ações","imgpreset");
        if (fa.showOpenDialog(parent)) {
            file = fa.getSelectedFile();
            try {
                String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                return deserialize(json);
            } catch (IOException | IllegalArgumentException e) {
                JOptionPane.showMessageDialog(parent,
                        "Erro ao carregar preset:\n" + e.getMessage(),
                        "Erro", JOptionPane.ERROR_MESSAGE);
                return null;
            }
        }else {
            return null;
        }

//        JFileChooser fc = buildChooser();
//        fc.setDialogTitle("Carregar preset de ações");
//        if (fc.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return null;
//
//        File file = fc.getSelectedFile();
//        try {
//            String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
//            return deserialize(json);
//        } catch (IOException | IllegalArgumentException e) {
//            JOptionPane.showMessageDialog(parent,
//                    "Erro ao carregar preset:\n" + e.getMessage(),
//                    "Erro", JOptionPane.ERROR_MESSAGE);
//            return null;
//        }
    }

    // ══════════════════════════════════════════════════════════════
    // SERIALIZAÇÃO  (List<ActionCardPanel> → JSON string)
    // ══════════════════════════════════════════════════════════════

    private static String serialize(List<ActionCardPanel> cards) {
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < cards.size(); i++) {
            ImageEditAction action = cards.get(i).getAction();
            sb.append("  ").append(toJsonObject(action));
            if (i < cards.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String toJsonObject(ImageEditAction action) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("enabled", String.valueOf(action.isEnabled()));

        switch (action) {
            case ImageAdjustAction a -> {
                fields.put("type",       q("adjust"));
                fields.put("brightness", String.valueOf(a.getBrightness()));
                fields.put("contrast",   String.valueOf(a.getContrast()));
                fields.put("gamma",      String.valueOf(a.getGamma()));
                fields.put("saturation", String.valueOf(a.getSaturation()));
            }
            case ImageRotateAction r -> {
                fields.put("type",      q("rotate"));
                fields.put("transform", q(r.getTransform().name()));
            }
            case ImageResizeAction s -> {
                fields.put("type", q("resize"));
                fields.put("mode", q(s.getMode().name()));
                if (s.getMode() == ImageResizeAction.Mode.PERCENTAGE) {
                    fields.put("percentage", String.valueOf(s.getPercentage()));
                } else {
                    fields.put("targetWidth",  String.valueOf(s.getTargetWidth()));
                    fields.put("targetHeight", String.valueOf(s.getTargetHeight()));
                }
            }
            case ImageCropAction c -> {
                fields.put("type",   q("crop"));
                fields.put("aspect", q(c.getAspectPreset().name()));
                if (c.hasRegion()) {
                    double[] r = c.getNormalizedRegion();
                    fields.put("normX", String.valueOf(r[0]));
                    fields.put("normY", String.valueOf(r[1]));
                    fields.put("normW", String.valueOf(r[2]));
                    fields.put("normH", String.valueOf(r[3]));
                }
            }
            case ImageSketchAction sk -> {
                fields.put("type",             q("sketch"));
                fields.put("effectApplied",    String.valueOf(sk.isEffectApplied()));
                fields.put("kernelSize",        String.valueOf(sk.getKernelSize()));
                fields.put("dilateIterations",  String.valueOf(sk.getDilateIterations()));
            }
            case ImagePaintBrushAction pb -> {
                fields.put("type",      q("paintbrush"));
                fields.put("colorR",    String.valueOf(pb.getBrushColor().getRed()));
                fields.put("colorG",    String.valueOf(pb.getBrushColor().getGreen()));
                fields.put("colorB",    String.valueOf(pb.getBrushColor().getBlue()));
                fields.put("opacity",   String.valueOf(pb.getOpacity()));
                fields.put("brushSize", String.valueOf(pb.getBrushSize()));
                fields.put("brushType", q(pb.getBrushType().name()));
                // Nota: a máscara de pintura não é serializada (dados visuais complexos).
                // Ao recarregar o preset, o card é recriado sem a máscara (efeito inativo).
            }
            case ImageBlurBrushAction bb -> {
                fields.put("type",       q("blurbrush"));
                fields.put("blurRadius", String.valueOf(bb.getBlurRadius()));
                fields.put("brushSize",  String.valueOf(bb.getBrushSize()));
                fields.put("brushType",  q(bb.getBrushType().name()));
                // Máscara não serializada (igual aos outros brush)
            }
            default -> fields.put("type", q("unknown"));
        }

        // Monta objeto JSON manualmente
        StringBuilder obj = new StringBuilder("{");
        fields.forEach((k, v) -> obj.append(q(k)).append(":").append(v).append(","));
        obj.deleteCharAt(obj.length() - 1);  // remove última vírgula
        obj.append("}");
        return obj.toString();
    }

    // ══════════════════════════════════════════════════════════════
    // DESSERIALIZAÇÃO  (JSON string → List<ImageEditAction>)
    // ══════════════════════════════════════════════════════════════

    private static List<ImageEditAction> deserialize(String json) {
        List<ImageEditAction> result = new ArrayList<>();
        // Extrai cada objeto {...} do array
        for (String obj : splitObjects(json)) {
            Map<String, String> fields = parseObject(obj);
            String type = unq(fields.getOrDefault("type", ""));
            boolean enabled = Boolean.parseBoolean(fields.getOrDefault("enabled", "true"));

            ImageEditAction action = switch (type) {
                case "adjust" -> {
                    ImageAdjustAction a = new ImageAdjustAction();
                    a.setBrightness(dbl(fields, "brightness", 0.0));
                    a.setContrast  (dbl(fields, "contrast",   1.0));
                    a.setGamma     (dbl(fields, "gamma",      1.0));
                    a.setSaturation(dbl(fields, "saturation", 1.0));
                    yield a;
                }
                case "rotate" -> {
                    ImageRotateAction r = new ImageRotateAction();
                    r.setTransform(ImageRotateAction.Transform.valueOf(
                            unq(fields.getOrDefault("transform", "NONE"))));
                    yield r;
                }
                case "resize" -> {
                    ImageResizeAction s = new ImageResizeAction();
                    ImageResizeAction.Mode mode = ImageResizeAction.Mode.valueOf(
                            unq(fields.getOrDefault("mode", "MANUAL")));
                    s.setMode(mode);
                    if (mode == ImageResizeAction.Mode.PERCENTAGE) {
                        s.setPercentage(dbl(fields, "percentage", 100.0));
                    } else {
                        s.setTargetWidth (itg(fields, "targetWidth",  -1));
                        s.setTargetHeight(itg(fields, "targetHeight", -1));
                    }
                    yield s;
                }
                case "crop" -> {
                    ImageCropAction c = new ImageCropAction();
                    c.setAspectPreset(ImageCropAction.AspectRatioPreset.valueOf(
                            unq(fields.getOrDefault("aspect", "FREE"))));
                    if (fields.containsKey("normX")) {
                        c.setCropRegionNormalized(
                                dbl(fields, "normX", 0), dbl(fields, "normY", 0),
                                dbl(fields, "normW", 1), dbl(fields, "normH", 1));
                    }
                    yield c;
                }
                case "sketch" -> {
                    ImageSketchAction sk = new ImageSketchAction();
                    sk.setKernelSize      (itg(fields, "kernelSize",       5));
                    sk.setDilateIterations(itg(fields, "dilateIterations", 1));
                    sk.setEffectApplied   (Boolean.parseBoolean(
                            fields.getOrDefault("effectApplied", "false")));
                    yield sk;
                }
                case "paintbrush" -> {
                    ImagePaintBrushAction pb = new ImagePaintBrushAction();
                    int cr = itg(fields, "colorR", 255);
                    int cg = itg(fields, "colorG", 0);
                    int cb = itg(fields, "colorB", 0);
                    pb.setBrushColor(new Color(cr, cg, cb));
                    pb.setOpacity   (Float.parseFloat(fields.getOrDefault("opacity", "1.0")));
                    pb.setBrushSize (itg(fields, "brushSize", 40));
                    pb.setBrushType (ImagePaintBrushAction.BrushType.valueOf(
                            unq(fields.getOrDefault("brushType", "SOFT"))));
                    yield pb;
                }

                case "blurbrush" -> {
                    ImageBlurBrushAction bb = new ImageBlurBrushAction();
                    bb.setBlurRadius(itg(fields, "blurRadius", 5));
                    bb.setBrushSize (itg(fields, "brushSize",  40));
                    bb.setBrushType (ImageBlurBrushAction.BrushType.valueOf(
                            unq(fields.getOrDefault("brushType", "SOFT"))));
                    yield bb;
                }
                default -> null;
            };

            if (action != null) {
                action.setEnabled(enabled);
                result.add(action);
            }
        }
        return result;
    }

    // ══════════════════════════════════════════════════════════════
    // PARSER JSON MÍNIMO (sem dependências externas)
    // ══════════════════════════════════════════════════════════════

    /** Divide o array JSON em strings de objeto individuais. */
    private static List<String> splitObjects(String json) {
        List<String> objs = new ArrayList<>();
        int depth = 0, start = -1;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') { if (depth++ == 0) start = i; }
            else if (c == '}') { if (--depth == 0 && start >= 0) objs.add(json.substring(start, i + 1)); }
        }
        return objs;
    }

    /** Converte um objeto JSON simples (sem arrays/objetos aninhados) em Map. */
    private static Map<String, String> parseObject(String obj) {
        Map<String, String> map = new LinkedHashMap<>();
        // Remove { e }
        String inner = obj.substring(1, obj.length() - 1).trim();
        // Divide por vírgulas que estão fora de aspas
        for (String pair : splitPairs(inner)) {
            int colon = pair.indexOf(':');
            if (colon < 0) continue;
            String key = pair.substring(0, colon).trim();
            String val = pair.substring(colon + 1).trim();
            map.put(key.replaceAll("\"", ""), val.replaceAll("\"", ""));
        }
        return map;
    }

    /** Divide pares chave:valor respeitando strings entre aspas. */
    private static List<String> splitPairs(String s) {
        List<String> pairs = new ArrayList<>();
        boolean inQuote = false;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') inQuote = !inQuote;
            else if (c == ',' && !inQuote) {
                pairs.add(s.substring(start, i).trim());
                start = i + 1;
            }
        }
        if (start < s.length()) pairs.add(s.substring(start).trim());
        return pairs;
    }

    // ── Helpers de conversão ──────────────────────────────────────
    private static String q(String s)                              { return "\"" + s + "\""; }
    private static String unq(String s)                            { return s.replaceAll("\"", ""); }
    private static double dbl(Map<String,String> m, String k, double def) {
        try { return Double.parseDouble(m.getOrDefault(k, String.valueOf(def))); }
        catch (NumberFormatException e) { return def; }
    }
    private static int itg(Map<String,String> m, String k, int def) {
        try { return Integer.parseInt(m.getOrDefault(k, String.valueOf(def))); }
        catch (NumberFormatException e) { return def; }
    }

    // ── FileChooser ───────────────────────────────────────────────
    private static JFileChooser buildChooser() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                EXT_DESC, EXT.substring(1)));
        fc.setAcceptAllFileFilterUsed(false);
        return fc;
    }

    private static File ensureExtension(File f) {
        return f.getName().endsWith(EXT) ? f : new File(f.getAbsolutePath() + EXT);
    }
}