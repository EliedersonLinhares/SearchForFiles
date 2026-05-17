package com.esl.searchforfiles.actions.imageEditor;

import java.util.LinkedHashMap;
import java.util.Map;

public class ImageEditAction {
    private final String name;
    private boolean enabled;

    // Parâmetros genéricos (dummy por enquanto — expanda por ação)
    private final Map<String, Object> params;

    public ImageEditAction(String name) {
        this.name    = name;
        this.enabled = true;
        this.params  = new LinkedHashMap<>();
    }

    public String getName()              { return name; }
    public boolean isEnabled()           { return enabled; }
    public void setEnabled(boolean v)    { enabled = v; }
    public Map<String, Object> getParams(){ return params; }
    public void setParam(String k, Object v){ params.put(k, v); }

    /** Texto resumido exibido no corpo do card de ação. */
    public String getSummary() {
        if (params.isEmpty()) return "(sem parâmetros)";
        StringBuilder sb = new StringBuilder();
        params.forEach((k, v) -> sb.append(k).append(": ").append(v).append("  "));
        return sb.toString().trim();
    }
}
