package com.esl.searchforfiles.preview.model3DViewer;

import java.awt.image.BufferedImage;
import java.util.Map;

public class MaterialInfo {
    public final String name;
    public final BufferedImage thumbnail;
    public final Map<String, String> textureBySlot;   // tipo de mapa -> nome do arquivo (ou null)
    public final Map<String, Boolean> slotSupported;   // tipo de mapa -> se o material suporta esse slot

    public MaterialInfo(String name, BufferedImage thumbnail,
                        Map<String, String> textureBySlot, Map<String, Boolean> slotSupported) {
        this.name = name;
        this.thumbnail = thumbnail;
        this.textureBySlot = textureBySlot;
        this.slotSupported = slotSupported;
    }
}
