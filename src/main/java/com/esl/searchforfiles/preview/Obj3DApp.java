package com.esl.searchforfiles.preview;


import com.jme3.app.SimpleApplication;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.*;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.*;
import com.jme3.scene.debug.WireBox;
import com.jme3.util.BufferUtils;
import com.jme3.scene.VertexBuffer.Type;

import java.nio.FloatBuffer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Aplicação jME3 embutida no Canvas AWT. Toda a lógica de câmera orbital,
 * carregamento de OBJ e manipulação da cena vive aqui.
 */
public class Obj3DApp extends SimpleApplication {

    // ── Estado da câmera orbital ──
    private float camYaw = 0f;
    private float camPitch = FastMath.QUARTER_PI * 0.5f;
    private float camDistance = 10f;
    private final Vector3f orbitTarget = new Vector3f(0, 0, 0);

    private boolean rotatingWithMouse = false;
    private boolean panningWithMouse = false;

    // ── Estado da cena ──
    private Node modelRoot;
    private Node gridNode;
    private DirectionalLight sun;
    private AmbientLight ambient;
    private boolean wireframe = false;

    private static final String USERDATA_ORIGINAL_MATERIAL = "originalMaterial";

    private static final ColorRGBA FLAT_GRAY = new ColorRGBA(0.65f, 0.65f, 0.65f, 1f);
    private static final ColorRGBA FLAT_GRAY_AMBIENT = new ColorRGBA(0.35f, 0.35f, 0.35f, 1f);

    private boolean flatGrayMode = true; // padrão: cinza sólido ao carregar

    private Runnable onModelLoaded; // callback para atualizar a UI Swing

    public Obj3DApp() {
        super(); // sem StatsAppState/FlyCamAppState padrão problemáticos em canvas
    }

    @Override
    public void simpleInitApp() {
        flyCam.setEnabled(false); // substituímos pelo nosso controle orbital
        setDisplayStatView(false);
        setDisplayFps(false);

        viewPort.setBackgroundColor(new ColorRGBA(0.16f, 0.16f, 0.18f, 1f));

        modelRoot = new Node("ModelRoot");
        rootNode.attachChild(modelRoot);

        setupLights();
        setupGrid();
        setupInput();
        updateCameraPosition();
    }

    private void setupLights() {
        sun = new DirectionalLight();
        sun.setDirection(new Vector3f(-0.5f, -1f, -0.5f).normalizeLocal());
        sun.setColor(ColorRGBA.White.mult(1.1f));
        rootNode.addLight(sun);

        ambient = new AmbientLight();
        ambient.setColor(ColorRGBA.White.mult(0.6f));
        rootNode.addLight(ambient);
    }

    private void setupGrid() {
        gridNode = new Node("Grid");
        Material gridMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        gridMat.setColor("Color", new ColorRGBA(0.4f, 0.4f, 0.45f, 1f));

        // Grade simples feita de linhas (WireBox reaproveitado como referência de escala)
        WireBox box = new WireBox(5f, 0.001f, 5f);
        Geometry gridGeom = new Geometry("GridLines", box);
        gridGeom.setMaterial(gridMat);
        gridNode.attachChild(gridGeom);

        rootNode.attachChild(gridNode);
    }

    // ── Entrada: arrastar com botão esquerdo = orbitar, botão direito = pan, scroll = zoom ──
    private void setupInput() {
        inputManager.addMapping("MouseRotate", new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addMapping("MousePan", new MouseButtonTrigger(MouseInput.BUTTON_RIGHT));
        inputManager.addMapping("MouseX+", new MouseAxisTrigger(MouseInput.AXIS_X, false));
        inputManager.addMapping("MouseX-", new MouseAxisTrigger(MouseInput.AXIS_X, true));
        inputManager.addMapping("MouseY+", new MouseAxisTrigger(MouseInput.AXIS_Y, false));
        inputManager.addMapping("MouseY-", new MouseAxisTrigger(MouseInput.AXIS_Y, true));
        inputManager.addMapping("MouseZoomIn", new MouseAxisTrigger(MouseInput.AXIS_WHEEL, false));
        inputManager.addMapping("MouseZoomOut", new MouseAxisTrigger(MouseInput.AXIS_WHEEL, true));

        ActionListener buttonListener = (name, isPressed, tpf) -> {
            if (name.equals("MouseRotate")) rotatingWithMouse = isPressed;
            if (name.equals("MousePan")) panningWithMouse = isPressed;
        };
        inputManager.addListener(buttonListener, "MouseRotate", "MousePan");

        AnalogListener analogListener = (name, value, tpf) -> {
            switch (name) {
                case "MouseX+" -> { if (rotatingWithMouse) orbit(-value * 3f, 0); else if (panningWithMouse) pan(-value, 0); }
                case "MouseX-" -> { if (rotatingWithMouse) orbit(value * 3f, 0); else if (panningWithMouse) pan(value, 0); }
                case "MouseY+" -> { if (rotatingWithMouse) orbit(0, value * 3f); else if (panningWithMouse) pan(0, value); }
                case "MouseY-" -> { if (rotatingWithMouse) orbit(0, -value * 3f); else if (panningWithMouse) pan(0, -value); }
                case "MouseZoomIn" -> zoom(-value * camDistance * 0.6f);
                case "MouseZoomOut" -> zoom(value * camDistance * 0.6f);
            }
        };
        inputManager.addListener(analogListener,
                "MouseX+", "MouseX-", "MouseY+", "MouseY-", "MouseZoomIn", "MouseZoomOut");
    }

    private void orbit(float deltaYaw, float deltaPitch) {
        camYaw += deltaYaw;
        camPitch = FastMath.clamp(camPitch + deltaPitch, -FastMath.HALF_PI + 0.05f, FastMath.HALF_PI - 0.05f);
        updateCameraPosition();
    }

    private void pan(float deltaX, float deltaY) {
        Vector3f right = cam.getLeft().mult(-deltaX * camDistance * 0.5f);
        Vector3f up = cam.getUp().mult(deltaY * camDistance * 0.5f);
        orbitTarget.addLocal(right).addLocal(up);
        updateCameraPosition();
    }

    private void zoom(float delta) {
        camDistance = FastMath.clamp(camDistance + delta, 0.5f, 500f);
        updateCameraPosition();
    }

    private void updateCameraPosition() {
        float x = camDistance * FastMath.cos(camPitch) * FastMath.sin(camYaw);
        float y = camDistance * FastMath.sin(camPitch);
        float z = camDistance * FastMath.cos(camPitch) * FastMath.cos(camYaw);
        Vector3f camPos = orbitTarget.add(x, y, z);
        cam.setLocation(camPos);
        cam.lookAt(orbitTarget, Vector3f.UNIT_Y);
    }

    public void loadObjFile(File objFile, Runnable onLoaded, Consumer<String> onError) {
        this.onModelLoaded = onLoaded;
        enqueue(() -> {
            try {
                modelRoot.detachAllChildren();

                File workingDir = prepareSanitizedModelFolder(objFile);
                assetManager.registerLocator(workingDir.getAbsolutePath(), FileLocator.class);

                Spatial loaded = assetManager.loadModel(objFile.getName());
                ensureNormals(loaded);
                captureOriginalMaterials(loaded);  // salva o material vindo do .mtl
                applyMaterialMode(loaded);         // aplica cinza ou original conforme o modo atual

                modelRoot.attachChild(loaded);
                frameModelInView(loaded);
                if (onLoaded != null) onLoaded.run();
            } catch (Exception ex) {
                ex.printStackTrace();
                if (onError != null) onError.accept(ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        });
    }
    private static final Pattern TEXTURE_DIRECTIVE = Pattern.compile(
            "^\\s*(map_Ka|map_Kd|map_Ks|map_Ns|map_d|map_bump|bump|disp|decal|refl)\\b(.*)$",
            Pattern.CASE_INSENSITIVE);


    private void sanitizeMtl(File source, File dest) throws IOException {
        List<String> lines = Files.readAllLines(source.toPath());
        List<String> cleaned = new ArrayList<>();
        for (String line : lines) {
            Matcher m = TEXTURE_DIRECTIVE.matcher(line);
            if (m.matches()) {
                String args = m.group(2) == null ? "" : m.group(2).trim();
                String[] tokens = args.isEmpty() ? new String[0] : args.split("\\s+");
                String lastToken = tokens.length > 0 ? tokens[tokens.length - 1] : "";
                boolean hasValidExtension = lastToken.contains(".")
                        && lastToken.lastIndexOf('.') < lastToken.length() - 1;
                if (!hasValidExtension) {
                    continue; // descarta a linha — textura vazia/malformada
                }
            }
            cleaned.add(line);
        }
        Files.write(dest.toPath(), cleaned);
    }

    private File prepareSanitizedModelFolder(File objFile) throws IOException {
        File tempDir = Files.createTempDirectory("obj3dviewer_").toFile();
        tempDir.deleteOnExit();

        File objCopy = new File(tempDir, objFile.getName());
        sanitizeObj(objFile, objCopy);

        File sourceDir = objFile.getParentFile();
        File[] mtlFiles = sourceDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".mtl"));
        if (mtlFiles != null) {
            for (File mtl : mtlFiles) {
                sanitizeMtl(mtl, new File(tempDir, mtl.getName()));
            }
        }
        return tempDir;
    }

    /**
     * Corrige os dois problemas mais comuns que quebram o OBJLoader do jME3:
     *  1. Arquivo sem quebra de linha final após a última diretiva (f/g/mtllib).
     *  2. Linhas "g" (grupo) vazias ou soltas no final do arquivo.
     */
    private void sanitizeObj(File source, File dest) throws IOException {
        List<String> lines = Files.readAllLines(source.toPath());
        List<String> cleaned = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();
            // Remove diretivas "g" sem nome de grupo (ex: linha "g" sozinha)
            if (trimmed.equals("g")) {
                continue;
            }
            cleaned.add(line);
        }

        // Remove linhas vazias/whitespace no final que podem confundir o Scanner
        while (!cleaned.isEmpty() && cleaned.get(cleaned.size() - 1).trim().isEmpty()) {
            cleaned.remove(cleaned.size() - 1);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(dest.toPath())) {
            for (String line : cleaned) {
                writer.write(line);
                writer.write('\n'); // GARANTE quebra de linha após CADA linha, inclusive a última
            }
        }
    }


//    private void ensureMaterial(Spatial spatial) {
//        if (spatial instanceof Geometry geom) {
//            if (geom.getMaterial() == null) {
//                Material mat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
//                mat.setBoolean("UseMaterialColors", true);
//                mat.setColor("Diffuse", ColorRGBA.LightGray);
//                mat.setColor("Ambient", ColorRGBA.Gray);
//                geom.setMaterial(mat);
//            }
//        } else if (spatial instanceof Node node) {
//            for (Spatial child : node.getChildren()) {
//                ensureMaterial(child);
//            }
//        }
//    }
    private void ensureMaterial(Spatial spatial) {
        if (spatial instanceof Geometry geom) {
            applyFlatGrayMaterial(geom);
        } else if (spatial instanceof Node node) {
            for (Spatial child : node.getChildren()) {
                ensureMaterial(child);
            }
        }
    }



    // Centraliza e enquadra a câmera de acordo com o bounding box do modelo carregado
    private void frameModelInView(Spatial model) {
        model.updateModelBound();
        com.jme3.bounding.BoundingVolume bv = model.getWorldBound();
        Vector3f center = bv.getCenter();
        float radius = (bv instanceof com.jme3.bounding.BoundingSphere bs)
                ? bs.getRadius()
                : model.getWorldBound().getVolume() > 0 ? 5f : 5f;

        orbitTarget.set(center);
        camDistance = Math.max(2f, radius * 2.5f);
        camYaw = FastMath.QUARTER_PI;
        camPitch = FastMath.QUARTER_PI * 0.5f;
        updateCameraPosition();
    }

    public void toggleWireframe() {
        enqueue(() -> {
            wireframe = !wireframe;
            applyWireframeRecursive(modelRoot, wireframe);
        });
    }

    private void applyWireframeRecursive(Spatial spatial, boolean enabled) {
        if (spatial instanceof Geometry geom && geom.getMaterial() != null) {
            geom.getMaterial().getAdditionalRenderState().setWireframe(enabled);
        } else if (spatial instanceof Node node) {
            for (Spatial child : node.getChildren()) applyWireframeRecursive(child, enabled);
        }
    }

    public void setGridVisible(boolean visible) {
        enqueue(() -> gridNode.setCullHint(visible ? Spatial.CullHint.Never : Spatial.CullHint.Always));
    }

    public void setBackgroundColor(ColorRGBA color) {
        enqueue(() -> viewPort.setBackgroundColor(color));
    }

    public void resetCamera() {
        enqueue(() -> {
            if (modelRoot.getChildren().isEmpty()) {
                orbitTarget.set(0, 0, 0);
                camDistance = 10f;
            } else {
                frameModelInView(modelRoot);
            }
            camYaw = FastMath.QUARTER_PI;
            camPitch = FastMath.QUARTER_PI * 0.5f;
            updateCameraPosition();
        });
    }

    public int countTriangles() {
        int[] total = {0};
        countTrianglesRecursive(modelRoot, total);
        return total[0];
    }

    private void countTrianglesRecursive(Spatial spatial, int[] total) {
        if (spatial instanceof Geometry geom) {
            total[0] += geom.getMesh().getTriangleCount();
        } else if (spatial instanceof Node node) {
            for (Spatial child : node.getChildren()) countTrianglesRecursive(child, total);
        }
    }

    @Override
    public void simpleUpdate(float tpf) {
        // espaço para animações/rotação automática, se desejar habilitar futuramente
    }

    // ── Chame isto logo após ensureMaterial(loaded), antes de attachChild ──
    private void ensureNormals(Spatial spatial) {
        if (spatial instanceof Geometry geom) {
            Mesh mesh = geom.getMesh();
            if (mesh.getBuffer(Type.Normal) == null) {
                generateSmoothNormals(mesh);
            }
        } else if (spatial instanceof Node node) {
            for (Spatial child : node.getChildren()) {
                ensureNormals(child);
            }
        }
    }

    private void generateSmoothNormals(Mesh mesh) {
        FloatBuffer posBuf = mesh.getFloatBuffer(Type.Position);
        if (posBuf == null) return;

        int vertCount = posBuf.limit() / 3;
        Vector3f[] positions = new Vector3f[vertCount];
        posBuf.rewind();
        for (int i = 0; i < vertCount; i++) {
            positions[i] = new Vector3f(posBuf.get(), posBuf.get(), posBuf.get());
        }

        Vector3f[] normalAccum = new Vector3f[vertCount];
        for (int i = 0; i < vertCount; i++) normalAccum[i] = new Vector3f();

        int[] indices = extractIndices(mesh);

        // Acumula a normal de cada triângulo em seus 3 vértices
        for (int i = 0; i < indices.length; i += 3) {
            int i0 = indices[i], i1 = indices[i + 1], i2 = indices[i + 2];

            Vector3f v0 = positions[i0];
            Vector3f v1 = positions[i1];
            Vector3f v2 = positions[i2];

            Vector3f edge1 = v1.subtract(v0);
            Vector3f edge2 = v2.subtract(v0);
            Vector3f faceNormal = edge1.cross(edge2); // não normaliza ainda: pondera por área

            normalAccum[i0].addLocal(faceNormal);
            normalAccum[i1].addLocal(faceNormal);
            normalAccum[i2].addLocal(faceNormal);
        }

        FloatBuffer normBuf = BufferUtils.createFloatBuffer(vertCount * 3);
        for (int i = 0; i < vertCount; i++) {
            Vector3f n = normalAccum[i];
            if (n.lengthSquared() < 1e-8f) {
                n.set(0, 1, 0); // fallback para vértices isolados/degenerados
            } else {
                n.normalizeLocal();
            }
            normBuf.put(n.x).put(n.y).put(n.z);
        }
        normBuf.rewind();

        mesh.setBuffer(Type.Normal, 3, normBuf);
        mesh.updateBound();
    }

    private int[] extractIndices(Mesh mesh) {
        var indexBuffer = mesh.getIndexBuffer();
        int count = indexBuffer.size();
        int[] result = new int[count];
        for (int i = 0; i < count; i++) {
            result[i] = indexBuffer.get(i);
        }
        return result;
    }

    private void reapplyMaterials(Spatial spatial) {
        if (spatial instanceof Geometry geom) {
            if (flatGrayMode) {
                applyFlatGrayMaterial(geom);
            } else {
                // Aqui você precisaria ter guardado o material original do MTLLoader
                // antes de sobrescrevê-lo, para poder restaurá-lo neste ramo.
            }
        } else if (spatial instanceof Node node) {
            for (Spatial child : node.getChildren()) reapplyMaterials(child);
        }
    }

    // ── Chame nesta ordem ao carregar o modelo ──
// ensureNormals(loaded);
// captureOriginalMaterials(loaded);   // NOVO: salva o material original antes de qualquer troca
// applyMaterialMode(loaded);          // NOVO: aplica cinza ou original conforme o modo atual

    /**
     * Percorre a hierarquia e guarda, em cada Geometry, o material que veio
     * originalmente do .obj/.mtl (via MTLLoader), como UserData.
     * Deve ser chamado uma única vez, logo após o modelo ser carregado.
     */
    private void captureOriginalMaterials(Spatial spatial) {
        if (spatial instanceof Geometry geom) {
            if (geom.getUserData(USERDATA_ORIGINAL_MATERIAL) == null) {
                geom.setUserData(USERDATA_ORIGINAL_MATERIAL, geom.getMaterial());
            }
        } else if (spatial instanceof Node node) {
            for (Spatial child : node.getChildren()) {
                captureOriginalMaterials(child);
            }
        }
    }

    /**
     * Aplica o modo de material atual (cinza sólido ou original) a toda a hierarquia,
     * usando o material salvo em captureOriginalMaterials.
     */
    private void applyMaterialMode(Spatial spatial) {
        if (spatial instanceof Geometry geom) {
            if (flatGrayMode) {
                applyFlatGrayMaterial(geom);
            } else {
                restoreOriginalMaterial(geom);
            }
        } else if (spatial instanceof Node node) {
            for (Spatial child : node.getChildren()) {
                applyMaterialMode(child);
            }
        }
    }

    private void applyFlatGrayMaterial(Geometry geom) {
        boolean hasNormals = geom.getMesh().getBuffer(VertexBuffer.Type.Normal) != null;

        Material mat = new Material(assetManager,
                hasNormals ? "Common/MatDefs/Light/Lighting.j3md" : "Common/MatDefs/Misc/Unshaded.j3md");

        if (hasNormals) {
            mat.setBoolean("UseMaterialColors", true);
            mat.setColor("Diffuse", FLAT_GRAY);
            mat.setColor("Ambient", FLAT_GRAY_AMBIENT);
            mat.setColor("Specular", ColorRGBA.White.mult(0.3f));
            mat.setFloat("Shininess", 12f);
        } else {
            mat.setColor("Color", FLAT_GRAY);
        }

        // Preserva o estado de wireframe se já estiver ativo
        mat.getAdditionalRenderState().setWireframe(wireframe);

        geom.setMaterial(mat);
    }

    private void restoreOriginalMaterial(Geometry geom) {
        Object stored = geom.getUserData(USERDATA_ORIGINAL_MATERIAL);
        if (stored instanceof Material original) {
            original.getAdditionalRenderState().setWireframe(wireframe);
            geom.setMaterial(original);
        }
        // Se por algum motivo não houver material original salvo, mantém o atual
    }

    /**
     * Chamado pela UI Swing para alternar entre cinza sólido e material/textura original.
     */
    public void setFlatGrayMode(boolean enabled) {
        this.flatGrayMode = enabled;
        enqueue(() -> applyMaterialMode(modelRoot));
    }

    public boolean isFlatGrayMode() {
        return flatGrayMode;
    }

}