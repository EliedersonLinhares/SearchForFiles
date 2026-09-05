package com.esl.searchforfiles.preview.model3DViewer;


import com.jme3.app.SimpleApplication;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.bounding.BoundingBox;
import com.jme3.bounding.BoundingSphere;
import com.jme3.bounding.BoundingVolume;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.*;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.MatParam;
import com.jme3.material.MatParamTexture;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.Renderer;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.*;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.scene.debug.WireBox;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ImageRaster;
import com.jme3.util.BufferUtils;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.scene.mesh.IndexBuffer;

import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.IdentityHashMap;
import java.util.Set;
import java.util.Collections;

public class Obj3DApp extends SimpleApplication {

    private static final ColorRGBA KEY_LIGHT_BASE_COLOR = ColorRGBA.White;
    private static final ColorRGBA FILL_LIGHT_BASE_COLOR = new ColorRGBA(0.55f, 0.58f, 0.65f, 1f);
    private static final String USERDATA_ORIGINAL_MATERIAL = "originalMaterial";
    private static final ColorRGBA FLAT_GRAY = new ColorRGBA(0.65f, 0.65f, 0.65f, 1f);
    private static final ColorRGBA FLAT_GRAY_AMBIENT = new ColorRGBA(0.35f, 0.35f, 0.35f, 1f);
    private static final Pattern TEXTURE_DIRECTIVE = Pattern.compile(
            "^\\s*(map_Ka|map_Kd|map_Ks|map_Ns|map_d|map_bump|bump|disp|decal|refl)\\b(.*)$",
            Pattern.CASE_INSENSITIVE);
    private static final ColorRGBA MONOCHROME_DIFFUSE = new ColorRGBA(0.92f, 0.92f, 0.90f, 1f); // quase branco, leve quente
    private static final ColorRGBA MONOCHROME_AMBIENT = new ColorRGBA(0.55f, 0.55f, 0.55f, 1f);
    private static final ColorRGBA MONOCHROME_EDGE_COLOR = new ColorRGBA(0.35f, 0.35f, 0.35f, 1f); // cinza médio, não preto puro
    private static final float DRAG_ZOOM_SENSITIVITY = 0.8f; // menor = mais suave/lento
    private static final String USERDATA_GROUP_ID = "groupId";
    private static final int MATERIAL_THUMBNAIL_SIZE = 48;
    private static final Logger logger = Logger.getLogger(Obj3DApp.class.getName());
    private static final String USERDATA_ORIGINAL_MATERIAL_NAME = "originalMaterialName";
    private static final float DIMMED_ALPHA = 0.10f;
    private static final String[] TEXTURE_MAP_TYPES = {
            "ColorMap", "NormalMap", "DiffuseMap", "SpecularMap", "AmbientMap"
    };
    private final Vector3f orbitTarget = new Vector3f(0, 0, 0);
    private final Map<Geometry, java.util.List<Geometry>> penArtifacts = new HashMap<>();
    private final Map<Integer, Spatial> groupRegistry = new HashMap<>();
    private final Map<String, java.util.List<Geometry>> materialGroups = new LinkedHashMap<>();
    // Guarda o estado original (alpha, blend mode, bucket) de cada Geometry
    // dimida, para poder restaurar com exatidão ao desmarcar o destaque
//    private final Map<Geometry, Float> originalAlphaByGeometry = new HashMap<>();
//    private final Map<Geometry, BlendMode> originalBlendByGeometry = new HashMap<>();
    private final Map<Material, Float> originalAlphaByMaterial = new IdentityHashMap<>();
    private final Map<Material, RenderState.BlendMode> originalBlendByMaterial = new IdentityHashMap<>();
    private final Map<Geometry, Bucket> originalBucketByGeometry = new HashMap<>();
    private final Map<String, Material> namedMaterials = new LinkedHashMap<>();
    private final java.util.Set<String> registeredTextureLocators = new java.util.HashSet<>();
    // ── Estado da câmera orbital ──
    private float camYaw = 0f;
    private float camPitch = FastMath.QUARTER_PI * 0.5f;
    private float camDistance = 10f;
    private boolean rotatingWithMouse = false;
    private boolean panningWithMouse = false;
    // ── Estado da cena ──
    private Node modelRoot;
    private Node gridNode;
    private DirectionalLight sun;
    private AmbientLight ambient;
    private DirectionalLight fillLight;
    private boolean wireframe = false;
    private float keyLightIntensity = 1.1f;   // valor inicial (equivalente ao "White.mult(1.1f)" de antes)
    private float fillLightIntensity = 0.35f;
    private boolean flatGrayMode = true; // padrão: cinza sólido ao carregar
    private Runnable onModelLoaded; // callback para atualizar a UI Swing
    // ── Estado da luz orbitável ──
    private float lightYaw = -0.6f;
    private float lightPitch = 0.9f; // ~50° acima do horizonte
    private boolean ctrlPressed = false;
    private Geometry lightGizmo; // indicador visual opcional da direção da luz
    private boolean showLightGizmo = false;
    private CameraMode cameraMode = CameraMode.FREE;
    private Consumer<CameraMode> onCameraModeChanged; // notifica a UI Swing quando o modo muda internamente
    private RenderStyle renderStyle = RenderStyle.FLAT_GRAY;
    private ColorRGBA backgroundColorBeforePen = null;
    private float currentModelRadius = 5f;
    private float creaseAngleDegrees = 19f;
    private float outlineThicknessRatio = 0.002f;
    private volatile ToolMode toolMode = ToolMode.ORBIT;
    private float minZoomDistance = 0.5f;
    private float maxZoomDistance = 500f;
    private float dynamicFarPlane = 1000f;
    private int nextGroupId = 0;
    private String highlightedMaterialName = null;
    private File lastLoadedObjFile;



    public Obj3DApp() {

        super(); // sem StatsAppState/FlyCamAppState padrão problemáticos em canvas

    }

    @Override
    public void simpleInitApp() {
        flyCam.setEnabled(false);
        setDisplayStatView(false);
        setDisplayFps(false);

        viewPort.setBackgroundColor(new ColorRGBA(0.16f, 0.16f, 0.18f, 1f));

        modelRoot = new Node("ModelRoot");
        rootNode.attachChild(modelRoot);

        setupLights();
        setupLightGizmo();
        setupGrid();
        setupInput();
        updateCameraPosition();
    }

    private void setupLights() {
        sun = new DirectionalLight();
        rootNode.addLight(sun);
        updateLightDirection();
        applyKeyLightIntensity();

        fillLight = new DirectionalLight();
        rootNode.addLight(fillLight);
        updateFillLightDirection();
        applyFillLightIntensity();

        ambient = new AmbientLight();
        ambient.setColor(ColorRGBA.White.mult(0.6f));
        rootNode.addLight(ambient);
    }

    private void applyKeyLightIntensity() {
        sun.setColor(KEY_LIGHT_BASE_COLOR.mult(keyLightIntensity));
    }

    private void applyFillLightIntensity() {
        fillLight.setColor(FILL_LIGHT_BASE_COLOR.mult(fillLightIntensity));
    }

    @Override
    public void simpleUpdate(float tpf) {
        updateFillLightDirection();
    }

    /**
     * Mantém a luz de preenchimento sempre vindo "de trás da câmera",
     * como uma luz de capacete (headlight) bem fraca — garante que o lado
     * visível do modelo nunca fique totalmente preto, mesmo que a luz
     * principal esteja apontando para o lado oposto.
     */
    private void updateFillLightDirection() {
        if (fillLight == null) return;
        // A luz "viaja" na mesma direção que a câmera está olhando
        fillLight.setDirection(cam.getDirection().normalize());
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

    private void setupInput() {
        inputManager.addMapping("MouseRotate", new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addMapping("MousePan", new MouseButtonTrigger(MouseInput.BUTTON_RIGHT));
        inputManager.addMapping("MouseX+", new MouseAxisTrigger(MouseInput.AXIS_X, false));
        inputManager.addMapping("MouseX-", new MouseAxisTrigger(MouseInput.AXIS_X, true));
        inputManager.addMapping("MouseY+", new MouseAxisTrigger(MouseInput.AXIS_Y, false));
        inputManager.addMapping("MouseY-", new MouseAxisTrigger(MouseInput.AXIS_Y, true));
        inputManager.addMapping("MouseZoomIn", new MouseAxisTrigger(MouseInput.AXIS_WHEEL, false));
        inputManager.addMapping("MouseZoomOut", new MouseAxisTrigger(MouseInput.AXIS_WHEEL, true));

        // ── NOVO: rastreia se Ctrl (esquerdo ou direito) está pressionado ──
        inputManager.addMapping("CtrlModifier",
                new KeyTrigger(KeyInput.KEY_LCONTROL),
                new KeyTrigger(KeyInput.KEY_RCONTROL));

        ActionListener buttonListener = (name, isPressed, tpf) -> {
            if (name.equals("MouseRotate")) rotatingWithMouse = isPressed;
            if (name.equals("MousePan")) panningWithMouse = isPressed;
            if (name.equals("CtrlModifier")) ctrlPressed = isPressed; // NOVO
        };
        inputManager.addListener(buttonListener, "MouseRotate", "MousePan", "CtrlModifier");

        AnalogListener analogListener = (name, value, tpf) -> {
            switch (name) {
                case "MouseX+" -> onMouseDeltaX(-value);
                case "MouseX-" -> onMouseDeltaX(value);
                case "MouseY+" -> onMouseDeltaY(value);
                case "MouseY-" -> onMouseDeltaY(-value);
                case "MouseZoomIn" -> zoom(-value * camDistance * 0.6f);   // scroll sempre funciona
                case "MouseZoomOut" -> zoom(value * camDistance * 0.6f);   // independente da ferramenta
            }
        };
        inputManager.addListener(analogListener,
                "MouseX+", "MouseX-", "MouseY+", "MouseY-", "MouseZoomIn", "MouseZoomOut");
    }

    private void onMouseDeltaX(float dx) {
        if (rotatingWithMouse) { // botão esquerdo pressionado
            if (ctrlPressed) {
                orbitLight(dx * 3f, 0);
            } else if (toolMode == ToolMode.PAN) {
                pan(dx, 0);
            } else if (toolMode == ToolMode.ORBIT) {
                orbit(dx * 3f, 0);
            }
            // ToolMode.ZOOM ignora o eixo horizontal de propósito —
            // a ferramenta de lupa só reage ao arrasto vertical
        } else if (panningWithMouse) { // botão direito — sempre pan, qualquer ferramenta
            pan(dx, 0);
        }
    }

    private void onMouseDeltaY(float dy) {
        if (rotatingWithMouse) {
            if (ctrlPressed) {
                orbitLight(0, dy * 3f);
            } else if (toolMode == ToolMode.PAN) {
                pan(0, dy);
            } else if (toolMode == ToolMode.ZOOM) {
                applyDragZoom(dy);
            } else if (toolMode == ToolMode.ORBIT) {
                orbit(0, dy * 3f);
            }
        } else if (panningWithMouse) {
            pan(0, dy);
        }
    }

    /**
     * Zoom suave por arrasto vertical: para cima aproxima (zoom in),
     * para baixo afasta (zoom out). A sensibilidade é proporcional à
     * distância atual da câmera, para que o efeito seja igualmente suave
     * tanto de perto quanto de longe do modelo.
     */
    private void applyDragZoom(float dy) {
        float delta = -dy * camDistance * DRAG_ZOOM_SENSITIVITY;
        zoom(delta);
    }

    private void orbitLight(float deltaYaw, float deltaPitch) {
        lightYaw += deltaYaw;
        lightPitch = FastMath.clamp(lightPitch + deltaPitch, -FastMath.HALF_PI + 0.05f, FastMath.HALF_PI - 0.05f);
        updateLightDirection();
    }

    /**
     * Recalcula o vetor de direção da luz a partir de lightYaw/lightPitch,
     * usando a mesma convenção esférica já usada para a câmera.
     * DirectionalLight.direction aponta PARA ONDE a luz viaja, então
     * negativamos a posição "esférica" do sol para obter esse vetor.
     */
    private void updateLightDirection() {
        float x = FastMath.cos(lightPitch) * FastMath.sin(lightYaw);
        float y = FastMath.sin(lightPitch);
        float z = FastMath.cos(lightPitch) * FastMath.cos(lightYaw);

        Vector3f sunPosition = new Vector3f(x, y, z);
        Vector3f direction = sunPosition.negate().normalizeLocal();

        sun.setDirection(direction);
        updateLightGizmo(sunPosition);
    }

    public void resetLight() {
        enqueue(() -> {
            lightYaw = -0.6f;
            lightPitch = 0.9f;
            updateLightDirection();
        });
    }

    private void setupLightGizmo() {
        Material gizmoMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        gizmoMat.setColor("Color", ColorRGBA.Yellow);

        com.jme3.scene.shape.Sphere sphere = new com.jme3.scene.shape.Sphere(8, 8, 0.15f);
        lightGizmo = new Geometry("LightGizmo", sphere);
        lightGizmo.setMaterial(gizmoMat);
        lightGizmo.setCullHint(Spatial.CullHint.Always); // começa oculto
        rootNode.attachChild(lightGizmo);
    }

    private void updateLightGizmo(Vector3f sunPosition) {
        if (lightGizmo == null) return;
        float gizmoDistance = Math.max(3f, camDistance * 0.9f);
        lightGizmo.setLocalTranslation(orbitTarget.add(sunPosition.mult(gizmoDistance)));
    }

    public void setLightGizmoVisible(boolean visible) {
        this.showLightGizmo = visible;
        enqueue(() -> {
            if (lightGizmo != null) {
                lightGizmo.setCullHint(visible ? Spatial.CullHint.Never : Spatial.CullHint.Always);
            }
        });
    }

    private void pan(float deltaX, float deltaY) {
        Vector3f right = cam.getLeft().mult(-deltaX * camDistance * 0.5f);
        Vector3f up = cam.getUp().mult(deltaY * camDistance * 0.5f);
        orbitTarget.addLocal(right).addLocal(up);
        updateCameraPosition();
    }

    private void zoom(float delta) {
        camDistance = FastMath.clamp(camDistance + delta, minZoomDistance, maxZoomDistance); // ANTES: 0.5f, 500f fixos
        updateCameraPosition();
    }

    private void updateCameraPosition() {
        switch (cameraMode) {
            case FREE -> updateFreeCameraPosition();
            case FRONT -> setFixedCamera(new Vector3f(0, 0, 1), Vector3f.UNIT_Y);
            case BACK -> setFixedCamera(new Vector3f(0, 0, -1), Vector3f.UNIT_Y);
            case LEFT -> setFixedCamera(new Vector3f(-1, 0, 0), Vector3f.UNIT_Y);
            case RIGHT -> setFixedCamera(new Vector3f(1, 0, 0), Vector3f.UNIT_Y);
            case TOP -> setFixedCamera(new Vector3f(0, 1, 0), Vector3f.UNIT_Z);
            case BOTTOM -> setFixedCamera(new Vector3f(0, -1, 0), new Vector3f(0, 0, -1));
        }
    }

    // Este é o antigo corpo de updateCameraPosition() — mantém a órbita livre inalterada
    private void updateFreeCameraPosition() {
        float x = camDistance * FastMath.cos(camPitch) * FastMath.sin(camYaw);
        float y = camDistance * FastMath.sin(camPitch);
        float z = camDistance * FastMath.cos(camPitch) * FastMath.cos(camYaw);
        Vector3f camPos = orbitTarget.add(x, y, z);
        cam.setLocation(camPos);
        cam.lookAt(orbitTarget, Vector3f.UNIT_Y);
    }

    /**
     * Posiciona a câmera em uma direção fixa relativa ao alvo (orbitTarget),
     * respeitando a distância atual (zoom). O vetor "up" precisa ser diferente
     * de UNIT_Y para Top/Bottom, já que olhar diretamente para cima/baixo
     * no eixo Y torna UNIT_Y degenerado como referência de "cima da tela".
     */
    private void setFixedCamera(Vector3f directionFromTarget, Vector3f upVector) {
        Vector3f camPos = orbitTarget.add(directionFromTarget.mult(camDistance));
        cam.setLocation(camPos);
        cam.lookAt(orbitTarget, upVector);
    }

    private void orbit(float deltaYaw, float deltaPitch) {
        if (cameraMode != CameraMode.FREE) {
            syncFreeAnglesFromFixedMode(); // evita um "salto" brusco de ângulo
            setCameraModeInternal(CameraMode.FREE);
        }
        camYaw += deltaYaw;
        camPitch = FastMath.clamp(camPitch + deltaPitch, -FastMath.HALF_PI + 0.05f, FastMath.HALF_PI - 0.05f);
        updateCameraPosition();
    }

    /**
     * Antes de sair de uma vista fixa por arrasto, ajusta camYaw/camPitch
     * (usados pelo modo livre) para o equivalente da vista fixa atual,
     * assim a transição fica suave em vez de "pular" para o último ângulo livre salvo.
     */
    private void syncFreeAnglesFromFixedMode() {
        switch (cameraMode) {
            case FRONT -> {
                camYaw = 0f;
                camPitch = 0f;
            }
            case BACK -> {
                camYaw = FastMath.PI;
                camPitch = 0f;
            }
            case LEFT -> {
                camYaw = -FastMath.HALF_PI;
                camPitch = 0f;
            }
            case RIGHT -> {
                camYaw = FastMath.HALF_PI;
                camPitch = 0f;
            }
            case TOP -> camPitch = FastMath.HALF_PI - 0.06f;
            case BOTTOM -> camPitch = -(FastMath.HALF_PI - 0.06f);
            default -> {
            }
        }
    }

    public void loadObjFile(File objFile, Runnable onLoaded, Consumer<String> onError) {
        this.lastLoadedObjFile = objFile; // NOVO
        this.registeredTextureLocators.clear(); // NOVO: começa "limpo" a cada novo modelo aberto
        this.onModelLoaded = onLoaded;
        enqueue(() -> {
            try {
                modelRoot.detachAllChildren();

                groupRegistry.clear(); // NOVO: evita IDs "fantasmas" apontando pro modelo anterior
                nextGroupId = 0;       // NOVO
                materialGroups.clear();          // NOVO
                namedMaterials.clear();
                highlightedMaterialName = null;  // NOVO
                originalAlphaByMaterial.clear();   // ANTES: originalAlphaByGeometry.clear();
                originalBlendByMaterial.clear();
                originalBucketByGeometry.clear();// NOVO

                File workingDir = prepareSanitizedModelFolder(objFile);
                assetManager.registerLocator(workingDir.getAbsolutePath(), FileLocator.class);
                assetManager.registerLocator(objFile.getParentFile().getAbsolutePath(), FileLocator.class);

                Spatial loaded = assetManager.loadModel(objFile.getName());
                ensureNormals(loaded);
                captureOriginalMaterials(loaded);
                applyMaterialMode(loaded);

                modelRoot.attachChild(loaded);
                frameModelInView(loaded);
                if (onLoaded != null) onLoaded.run();
            } catch (Exception ex) {
                ex.printStackTrace();
                if (onError != null) onError.accept(ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        });
    }

    /**
     * Registra uma pasta adicional como fonte de texturas e recarrega o modelo
     * atual, limpando o cache do AssetManager para forçar a re-resolução de
     * texturas que anteriormente falharam.
     */
    public void addTextureSearchPathAndReload(File folder, Runnable onReloaded, Consumer<String> onError) {
        if (lastLoadedObjFile == null) {
            if (onError != null) onError.accept("Nenhum modelo carregado no momento.");
            return;
        }

        String path = folder.getAbsolutePath();
        enqueue(() -> {
            if (registeredTextureLocators.add(path)) {
                assetManager.registerLocator(path, FileLocator.class);
            }

            // Essencial: limpa o cache para forçar o AssetManager a tentar de novo
            // com o novo locator, em vez de reaproveitar o material com textura ausente
            assetManager.clearCache();

            try {
                modelRoot.detachAllChildren();

                File workingDir = prepareSanitizedModelFolder(lastLoadedObjFile);
                assetManager.registerLocator(workingDir.getAbsolutePath(), FileLocator.class);

                Spatial loaded = assetManager.loadModel(lastLoadedObjFile.getName());
                ensureNormals(loaded);
                captureOriginalMaterials(loaded);
                applyMaterialMode(loaded);

                modelRoot.attachChild(loaded);
                frameModelInView(loaded);
                if (onReloaded != null) onReloaded.run();
            } catch (Exception ex) {
                ex.printStackTrace();
                if (onError != null) onError.accept(ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        });
    }

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
     * 1. Arquivo sem quebra de linha final após a última diretiva (f/g/mtllib).
     * 2. Linhas "g" (grupo) vazias ou soltas no final do arquivo.
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

    private void ensureMaterial(Spatial spatial) {
        if (spatial instanceof Geometry geom) {
            applyFlatGrayMaterial(geom);
        } else if (spatial instanceof Node node) {
            for (Spatial child : node.getChildren()) {
                ensureMaterial(child);
            }
        }
    }

    private void frameModelInView(Spatial model) {
        model.updateModelBound();
        BoundingVolume bv = model.getWorldBound();
        Vector3f center = bv.getCenter();
        float radius = computeBoundingRadius(bv); // CORRIGIDO (antes: só funcionava para BoundingSphere)
        currentModelRadius = radius;

        orbitTarget.set(center);
        camDistance = Math.max(2f, radius * 2.5f);
        camYaw = FastMath.QUARTER_PI;
        camPitch = FastMath.QUARTER_PI * 0.5f;

        updateCameraLimitsForModelSize(radius);
        updateCameraPosition();
    }

    /**
     * Recalcula os limites de zoom e o plano de corte distante (far plane)
     * proporcionalmente ao tamanho do modelo carregado. Sem isso, modelos
     * muito grandes ficam com partes cortadas pelo far plane fixo do jME (1000
     * por padrão), e o zoom fica limitado demais para conseguir enquadrar o
     * modelo inteiro na tela.
     */
    private void updateCameraLimitsForModelSize(float modelRadius) {
        minZoomDistance = Math.max(0.01f, modelRadius * 0.01f); // evita "entrar" demais dentro do modelo
        maxZoomDistance = Math.max(500f, modelRadius * 15f);    // margem generosa para dar zoom out total

        // O far plane precisa cobrir a distância máxima de zoom MAIS o próprio
        // tamanho do modelo (senão o lado mais distante do modelo, mesmo visto
        // de longe, continua sendo cortado)
        dynamicFarPlane = maxZoomDistance + modelRadius * 3f;
        float dynamicNearPlane = Math.max(0.001f, modelRadius * 0.001f); // evita z-fighting em modelos minúsculos

        cam.setFrustumPerspective(45f, (float) cam.getWidth() / cam.getHeight(),
                dynamicNearPlane, dynamicFarPlane);
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
                frameModelInView(modelRoot); // já corrigido internamente, nenhuma mudança adicional aqui
            }
            camYaw = FastMath.QUARTER_PI;
            camPitch = FastMath.QUARTER_PI * 0.5f;
            setCameraModeInternal(CameraMode.FREE);
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
    public void setRenderStyle(RenderStyle style) {
        boolean enteringPen = style == RenderStyle.PEN && renderStyle != RenderStyle.PEN;
        boolean leavingPen = style != RenderStyle.PEN && renderStyle == RenderStyle.PEN;

        this.renderStyle = style;
        enqueue(() -> {
            if (enteringPen) {
                backgroundColorBeforePen = viewPort.getBackgroundColor().clone();
                viewPort.setBackgroundColor(ColorRGBA.White);
            } else if (leavingPen && backgroundColorBeforePen != null) {
                viewPort.setBackgroundColor(backgroundColorBeforePen);
                backgroundColorBeforePen = null;
            }
            applyMaterialMode(modelRoot);

            // NOVO: reaplica o destaque de material, já que os materiais foram recriados
            originalAlphaByMaterial.clear();   // ANTES: originalAlphaByGeometry.clear();
            originalBlendByMaterial.clear();
            originalBucketByGeometry.clear();
            applyMaterialHighlightRecursive(modelRoot);
        });
    }

    // ── Chame nesta ordem ao carregar o modelo ──
    private void applyMaterialMode(Spatial spatial) {
        if (spatial instanceof Geometry geom) {
            removePenArtifacts(geom);
            switch (renderStyle) {
                case FLAT_GRAY -> applyFlatGrayMaterial(geom);
                case ORIGINAL_MATERIAL -> restoreOriginalMaterial(geom);
                case PEN -> applyPenStyle(geom);
                case MONOCHROME -> applyMonochromeStyle(geom);
            }
        } else if (spatial instanceof Node node) {
            for (Spatial child : node.getChildren()) {
                applyMaterialMode(child);
            }
        }
    }

    private void applyMaterialHighlightRecursive(Spatial spatial) {
        // processedMaterials garante que cada Material único (mesmo que
        // compartilhado por várias Geometry) seja escurecido/restaurado
        // apenas UMA vez nesta passada, evitando ler um alpha já mutado
        // pela Geometry anterior como se fosse o valor "original"
        Set<Material> processedMaterials = Collections.newSetFromMap(new IdentityHashMap<>());
        applyMaterialHighlightRecursive(spatial, processedMaterials);
    }

//    private void applyMaterialHighlightRecursive(Spatial spatial, Set<Material> processedMaterials) {
//        if (spatial instanceof Geometry geom) {
//            Object nameData = geom.getUserData(USERDATA_ORIGINAL_MATERIAL_NAME);
//            String geomMaterialName = (nameData instanceof String s) ? s : null;
//
//            boolean shouldDim = highlightedMaterialName != null
//                    && !highlightedMaterialName.equals(geomMaterialName);
//
//            setGeometryDimmed(geom, shouldDim, processedMaterials);
//        } else if (spatial instanceof Node node) {
//            for (Spatial child : node.getChildren()) {
//                applyMaterialHighlightRecursive(child, processedMaterials);
//            }
//        }
//    }

    private void applyMonochromeStyle(Geometry geom) {
        Node parent = geom.getParent();
        if (parent == null) return;

        // 1. Material "clay": sombreamento real com luz, mas sem cor/textura do arquivo original
        boolean hasNormals = geom.getMesh().getBuffer(Type.Normal) != null;
        Material mat = new Material(assetManager,
                hasNormals ? "Common/MatDefs/Light/Lighting.j3md" : "Common/MatDefs/Misc/Unshaded.j3md");

        if (hasNormals) {
            mat.setBoolean("UseMaterialColors", true);
            mat.setColor("Diffuse", MONOCHROME_DIFFUSE);
            mat.setColor("Ambient", MONOCHROME_AMBIENT);
            mat.setColor("Specular", ColorRGBA.White.mult(0.15f)); // brilho bem sutil, tipo cerâmica fosca
            mat.setFloat("Shininess", 8f);
        } else {
            mat.setColor("Color", MONOCHROME_DIFFUSE);
        }
        geom.setMaterial(mat);
        geom.setQueueBucket(Bucket.Opaque);

        // 2. Reaproveita a mesma geração de arestas de vinco do modo Pen
        java.util.List<Geometry> artifacts = new java.util.ArrayList<>();
        Geometry creaseLines = buildCreaseEdgeGeometry(geom);
        if (creaseLines != null) {
            Material lineMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            lineMat.setColor("Color", MONOCHROME_EDGE_COLOR);
            lineMat.getAdditionalRenderState().setDepthTest(true);
            lineMat.getAdditionalRenderState().setDepthWrite(false);
            lineMat.getAdditionalRenderState().setPolyOffset(-2f, -2f);
            lineMat.getAdditionalRenderState().setLineWidth(1.2f); // um pouco mais fina que no modo Pen
            creaseLines.setMaterial(lineMat);
            creaseLines.setQueueBucket(Bucket.Transparent); // depois do sólido, para o depth test funcionar
            parent.attachChild(creaseLines);
            artifacts.add(creaseLines);
        }
        // Nota: sem contorno de silhueta aqui — a iluminação já define a borda do objeto

        penArtifacts.put(geom, artifacts);
    }

    private void applyPenStyle(Geometry geom) {
        Node parent = geom.getParent();
        if (parent == null) return;

        Material depthOnlyMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        depthOnlyMat.setColor("Color", ColorRGBA.White);
        depthOnlyMat.getAdditionalRenderState().setColorWrite(false);
        geom.setMaterial(depthOnlyMat);
        geom.setQueueBucket(Bucket.Opaque); // garante que renderiza ANTES das linhas

        java.util.List<Geometry> artifacts = new java.util.ArrayList<>();

        // 2. Arestas de vinco — só as "quinas reais" do modelo, com remoção de linha oculta
        Geometry creaseLines = buildCreaseEdgeGeometry(geom);
        if (creaseLines != null) {
            Material lineMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            lineMat.setColor("Color", ColorRGBA.Black);
            lineMat.getAdditionalRenderState().setDepthTest(true);
            lineMat.getAdditionalRenderState().setDepthWrite(false);
            lineMat.getAdditionalRenderState().setPolyOffset(-2f, -2f); // evita z-fighting com o sólido
            lineMat.getAdditionalRenderState().setLineWidth(1.5f);
            creaseLines.setMaterial(lineMat);
            creaseLines.setQueueBucket(Bucket.Transparent); // renderiza DEPOIS do passo de profundidade
            parent.attachChild(creaseLines);
            artifacts.add(creaseLines);
        }


        Geometry outline = buildInvertedHullOutline(geom, currentModelRadius);
        if (outline != null) {
            Material outlineMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
            outlineMat.setColor("Color", ColorRGBA.Black);
            outlineMat.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Front);
            outline.setMaterial(outlineMat);
            outline.setQueueBucket(Bucket.Transparent); // ← CORRIGIDO (era Bucket.Opaque)
            parent.attachChild(outline);
            artifacts.add(outline);
        }

        penArtifacts.put(geom, artifacts);
    }

    private void removePenArtifacts(Geometry geom) {
        java.util.List<Geometry> artifacts = penArtifacts.remove(geom);
        if (artifacts != null) {
            for (Geometry artifact : artifacts) {
                if (artifact.getParent() != null) {
                    artifact.removeFromParent();
                }
            }
        }
    }

    private Geometry buildCreaseEdgeGeometry(Geometry sourceGeom) {
        Mesh mesh = sourceGeom.getMesh();
        FloatBuffer posBuf = mesh.getFloatBuffer(Type.Position);
        if (posBuf == null) return null;

        int vertCount = posBuf.limit() / 3;
        Vector3f[] positions = new Vector3f[vertCount];
        posBuf.rewind();
        for (int i = 0; i < vertCount; i++) {
            positions[i] = new Vector3f(posBuf.get(), posBuf.get(), posBuf.get());
        }

        int[] indices = extractIndices(mesh); // já existe (criado para as normais suaves)
        Map<Long, EdgeData> edgeMap = new HashMap<>();

        for (int t = 0; t < indices.length; t += 3) {
            int i0 = indices[t], i1 = indices[t + 1], i2 = indices[t + 2];
            Vector3f faceNormal = computeFaceNormal(positions[i0], positions[i1], positions[i2]);
            addEdge(edgeMap, i0, i1, faceNormal);
            addEdge(edgeMap, i1, i2, faceNormal);
            addEdge(edgeMap, i2, i0, faceNormal);
        }

        float creaseThresholdCos = FastMath.cos(creaseAngleDegrees * FastMath.DEG_TO_RAD);
        java.util.List<Integer> lineIndices = new java.util.ArrayList<>();

        for (EdgeData edge : edgeMap.values()) {
            boolean isBoundary = edge.faceCount == 1;          // borda aberta da malha
            boolean isNonManifold = edge.faceCount > 2;         // geometria não-manifold
            boolean isCrease = edge.faceCount == 2
                    && edge.normalA.dot(edge.normalB) < creaseThresholdCos; // quina real

            if (isBoundary || isCrease || isNonManifold) {
                lineIndices.add(edge.v0);
                lineIndices.add(edge.v1);
            }
        }
        if (lineIndices.isEmpty()) return null;

        Mesh lineMesh = new Mesh();
        lineMesh.setMode(Mesh.Mode.Lines);

        FloatBuffer linePosBuf = BufferUtils.createFloatBuffer(vertCount * 3);
        posBuf.rewind();
        linePosBuf.put(posBuf);
        linePosBuf.rewind();
        lineMesh.setBuffer(Type.Position, 3, linePosBuf);

        if (vertCount >= 65536) {
            IntBuffer ib = BufferUtils.createIntBuffer(lineIndices.size());
            for (int idx : lineIndices) ib.put(idx);
            ib.rewind();
            lineMesh.setBuffer(Type.Index, 2, ib);
        } else {
            ShortBuffer sb = BufferUtils.createShortBuffer(lineIndices.size());
            for (int idx : lineIndices) sb.put((short) (int) idx);
            sb.rewind();
            lineMesh.setBuffer(Type.Index, 2, sb);
        }

        lineMesh.updateBound();
        lineMesh.updateCounts();

        return new Geometry(sourceGeom.getName() + "-crease", lineMesh);
    }

    private Vector3f computeFaceNormal(Vector3f a, Vector3f b, Vector3f c) {
        Vector3f edge1 = b.subtract(a);
        Vector3f edge2 = c.subtract(a);
        return edge1.cross(edge2).normalizeLocal();
    }

    private void addEdge(Map<Long, EdgeData> edgeMap, int a, int b, Vector3f faceNormal) {
        int v0 = Math.min(a, b);
        int v1 = Math.max(a, b);
        long key = ((long) v0 << 32) | (v1 & 0xffffffffL);

        EdgeData data = edgeMap.get(key);
        if (data == null) {
            data = new EdgeData(v0, v1);
            edgeMap.put(key, data);
        }
        data.addFace(faceNormal);
    }

    private Geometry buildInvertedHullOutline(Geometry sourceGeom, float modelRadius) {
        Mesh sourceMesh = sourceGeom.getMesh();
        FloatBuffer posBuf = sourceMesh.getFloatBuffer(Type.Position);
        FloatBuffer normBuf = sourceMesh.getFloatBuffer(Type.Normal);
        if (posBuf == null || normBuf == null) return null; // precisa de normais para "inflar"

        float thickness = Math.max(0.001f, modelRadius * outlineThicknessRatio);
        int vertCount = posBuf.limit() / 3;
        FloatBuffer expandedPosBuf = BufferUtils.createFloatBuffer(vertCount * 3);

        posBuf.rewind();
        normBuf.rewind();
        for (int i = 0; i < vertCount; i++) {
            float px = posBuf.get(), py = posBuf.get(), pz = posBuf.get();
            float nx = normBuf.get(), ny = normBuf.get(), nz = normBuf.get();
            expandedPosBuf.put(px + nx * thickness).put(py + ny * thickness).put(pz + nz * thickness);
        }
        expandedPosBuf.rewind();

        Mesh outlineMesh = sourceMesh.deepClone();
        outlineMesh.clearBuffer(Type.Normal); // material Unshaded não precisa de normais
        outlineMesh.setBuffer(Type.Position, 3, expandedPosBuf);
        outlineMesh.updateBound();

        return new Geometry(sourceGeom.getName() + "-outline", outlineMesh);
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

    public boolean isFlatGrayMode() {
        return flatGrayMode;
    }

    /**
     * Chamado pela UI Swing para alternar entre cinza sólido e material/textura original.
     */
    public void setFlatGrayMode(boolean enabled) {
        this.flatGrayMode = enabled;
        enqueue(() -> applyMaterialMode(modelRoot));
    }

    public void setFillLightEnabled(boolean enabled) {
        enqueue(() -> fillLight.setColor(
                enabled ? new ColorRGBA(0.55f, 0.58f, 0.65f, 1f).mult(fillLightIntensity) : ColorRGBA.Black));
    }

    public float getKeyLightIntensity() {
        return keyLightIntensity;
    }

    /**
     * @param intensity valor de 0.0 a 3.0 (0 = luz principal apagada, 1.0 = padrão, 3.0 = bem forte)
     */
    public void setKeyLightIntensity(float intensity) {
        this.keyLightIntensity = FastMath.clamp(intensity, 0f, 3f);
        enqueue(this::applyKeyLightIntensity);
    }

    public float getFillLightIntensity() {
        return fillLightIntensity;
    }

    /**
     * @param intensity valor de 0.0 a 1.5 (0 = preenchimento apagado, 0.35 = padrão sutil)
     */
    public void setFillLightIntensity(float intensity) {
        this.fillLightIntensity = FastMath.clamp(intensity, 0f, 1.5f);
        enqueue(this::applyFillLightIntensity);
    }

    private void setCameraModeInternal(CameraMode mode) {
        this.cameraMode = mode;
        updateCameraPosition();
        if (onCameraModeChanged != null) {
            onCameraModeChanged.accept(mode);
        }
    }

    public void setOnCameraModeChanged(Consumer<CameraMode> callback) {
        this.onCameraModeChanged = callback;
    }

    public CameraMode getCameraMode() {
        return cameraMode;
    }

    public void setCameraMode(CameraMode mode) {
        enqueue(() -> setCameraModeInternal(mode));
    }

    public void setCreaseAngle(float degrees) {
        this.creaseAngleDegrees = FastMath.clamp(degrees, 1f, 90f);
        if (renderStyle == RenderStyle.PEN) {
            enqueue(() -> applyMaterialMode(modelRoot)); // reconstrói as linhas com o novo ângulo
        }
    }

    public void setOutlineThickness(float ratio) {
        this.outlineThicknessRatio = FastMath.clamp(ratio, 0f, 0.05f);
        if (renderStyle == RenderStyle.PEN) {
            enqueue(() -> applyMaterialMode(modelRoot));
        }
    }

    /**
     * Calcula um raio equivalente a partir do bounding volume do modelo,
     * suportando tanto BoundingSphere quanto BoundingBox (o padrão do jME3
     * para malhas .obj carregadas). Sem isso, modelos com BoundingBox caem
     * incorretamente em um valor fixo de fallback, ignorando o tamanho real.
     */
    private float computeBoundingRadius(BoundingVolume bv) {
        if (bv instanceof BoundingSphere bs) {
            return bs.getRadius();
        } else if (bv instanceof BoundingBox bb) {
            Vector3f extent = new Vector3f();
            bb.getExtent(extent); // metade do tamanho em cada eixo (half-extents)
            return extent.length(); // raio da esfera que envolve a caixa inteira
        }
        return 5f; // fallback só para volumes desconhecidos/nulos (caso raro)
    }

    /**
     * Constrói (na thread do jME) uma cópia leve da hierarquia de Spatials
     * atual, segura para ser exibida em uma JTree do Swing. Cada Spatial
     * recebe um ID numérico salvo como UserData, usado depois para localizá-lo
     * rapidamente ao alternar visibilidade a partir da UI.
     */
    public void requestGroupHierarchy(Consumer<GroupNode> callback) {
        enqueue(() -> {
            groupRegistry.clear();
            nextGroupId = 0;
            GroupNode root = buildGroupNode(modelRoot, "Modelo");
            if (callback != null) callback.accept(root);
        });
    }

    private GroupNode buildGroupNode(Spatial spatial, String fallbackName) {
        int id = nextGroupId++;
        spatial.setUserData(USERDATA_GROUP_ID, id);
        groupRegistry.put(id, spatial);

        boolean visible = spatial.getCullHint() != Spatial.CullHint.Always;
        String name = (spatial.getName() != null && !spatial.getName().isBlank())
                ? spatial.getName() : fallbackName;

        if (spatial instanceof Node node) {
            GroupNode groupNode = new GroupNode(id, name, false, visible);
            int childIndex = 0;
            for (Spatial child : node.getChildren()) {
                groupNode.children.add(buildGroupNode(child, "Parte " + (childIndex++)));
            }
            return groupNode;
        } else {
            return new GroupNode(id, name, true, visible);
        }
    }

    /**
     * Mostra ou esconde um grupo/parte específica pelo ID atribuído em
     * requestGroupHierarchy. Como o jME propaga CullHint.Inherit a partir do
     * ancestral mais próximo com valor explícito, esconder um grupo esconde
     * automaticamente tudo dentro dele — a menos que um descendente também
     * tenha sido alternado manualmente, caso em que o valor do descendente
     * prevalece sobre o do grupo pai.
     */
    public void setGroupVisible(int groupId, boolean visible) {
        enqueue(() -> {
            Spatial spatial = groupRegistry.get(groupId);
            if (spatial != null) {
                spatial.setCullHint(visible ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
            }
        });
    }

    /**
     * Mostra ou esconde TODOS os grupos/partes do modelo de uma vez.
     * O callback onDone roda depois que a mudança já foi aplicada na thread
     * do jME, para a UI Swing poder atualizar os checkboxes com segurança.
     */
    public void setAllGroupsVisible(boolean visible, Runnable onDone) {
        enqueue(() -> {
            setCullHintRecursive(modelRoot, visible);
            if (onDone != null) onDone.run();
        });
    }

    private void setCullHintRecursive(Spatial spatial, boolean visible) {
        spatial.setCullHint(visible ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        if (spatial instanceof Node node) {
            for (Spatial child : node.getChildren()) {
                setCullHintRecursive(child, visible);
            }
        }
    }

    /**
     * Percorre a hierarquia do modelo carregado, coleta os materiais originais
     * (do .mtl, não os materiais de exibição como Cinza Sólido/Pen) e gera uma
     * miniatura para cada um — a partir da textura difusa, se houver, ou de um
     * retângulo com a cor sólida do material.
     */
    public void requestMaterialsList(Consumer<java.util.List<MaterialInfo>> callback) {
        enqueue(() -> {
            namedMaterials.clear();
            materialGroups.clear();
            collectMaterials(modelRoot, namedMaterials); // agora popula direto o campo namedMaterials

            // Reconstrói materialGroups (usado pelo destaque) a partir da hierarquia atual
            collectMaterialGroups(modelRoot);

            java.util.List<MaterialInfo> result = new java.util.ArrayList<>();
            for (Map.Entry<String, Material> entry : namedMaterials.entrySet()) {
                result.add(buildMaterialInfo(entry.getKey(), entry.getValue()));
            }
            if (callback != null) callback.accept(result);
        });
    }

    private void collectMaterials(Spatial spatial, Map<String, Material> byName) {
        if (spatial instanceof Geometry geom) {
            Object original = geom.getUserData(USERDATA_ORIGINAL_MATERIAL);
            Material mat = (original instanceof Material m) ? m : geom.getMaterial();
            if (mat != null) {
                String name = mat.getName();
                if (name == null || name.isBlank()) {
                    name = "Material " + (byName.size() + 1);
                }
                byName.putIfAbsent(name, mat);
                geom.setUserData(USERDATA_ORIGINAL_MATERIAL_NAME, name);
            }
        } else if (spatial instanceof Node node) {
            for (Spatial child : node.getChildren()) {
                collectMaterials(child, byName);
            }
        }
    }

    private void collectMaterialGroups(Spatial spatial) {
        if (spatial instanceof Geometry geom) {
            Object nameData = geom.getUserData(USERDATA_ORIGINAL_MATERIAL_NAME);
            if (nameData instanceof String name) {
                materialGroups.computeIfAbsent(name, k -> new java.util.ArrayList<>()).add(geom);
            }
        } else if (spatial instanceof Node node) {
            for (Spatial child : node.getChildren()) {
                collectMaterialGroups(child);
            }
        }
    }

    private MaterialInfo buildMaterialInfo(String name, Material mat) {
        BufferedImage thumbnail = null;

        MatParamTexture previewParam = mat.getTextureParam("DiffuseMap");
        if (previewParam == null) previewParam = mat.getTextureParam("ColorMap");

        if (previewParam != null && previewParam.getTextureValue() instanceof Texture2D tex2D) {
            try {
                thumbnail = createTextureThumbnail(tex2D, MATERIAL_THUMBNAIL_SIZE);
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Falha ao gerar miniatura da textura do material {0}", name);
            }
        }
        if (thumbnail == null) {
            ColorRGBA swatchColor = ColorRGBA.LightGray;
            MatParam colorParam = mat.getParam("Diffuse");
            if (colorParam == null) colorParam = mat.getParam("Color");
            if (colorParam != null && colorParam.getValue() instanceof ColorRGBA c) {
                swatchColor = c;
            }
            thumbnail = createColorThumbnail(swatchColor, MATERIAL_THUMBNAIL_SIZE);
        }

        // NOVO: coleta o estado atual de cada slot de textura
        Map<String, String> textureBySlot = new LinkedHashMap<>();
        Map<String, Boolean> slotSupported = new LinkedHashMap<>();

        for (String slot : TEXTURE_MAP_TYPES) {
            boolean supported = mat.getMaterialDef().getMaterialParam(slot) != null;
            slotSupported.put(slot, supported);

            String textureName = null;
            if (supported) {
                MatParamTexture tp = mat.getTextureParam(slot);
                if (tp != null && tp.getTextureValue() != null && tp.getTextureValue().getKey() != null) {
                    textureName = tp.getTextureValue().getKey().getName();
                }
            }
            textureBySlot.put(slot, textureName);
        }

        return new MaterialInfo(name, thumbnail, textureBySlot, slotSupported);
    }

    /**
     * Converte a textura (dados de pixel crus do jME) em uma pequena miniatura
     * BufferedImage, amostrando pixels proporcionalmente ao tamanho de destino.
     * Usa ImageRaster, que sabe interpretar corretamente os diferentes formatos
     * de pixel (RGBA8, ABGR8, etc.) sem precisarmos tratar cada um manualmente.
     */
    private BufferedImage createTextureThumbnail(Texture2D tex, int size) throws Exception {
        Image image = tex.getImage();
        ImageRaster raster = ImageRaster.create(image); // lê a imagem já carregada em memória, sem precisar da GPU

        int srcW = image.getWidth();
        int srcH = image.getHeight();
        BufferedImage thumb = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < size; y++) {
            int sy = y * srcH / size;
            int flippedY = srcH - 1 - sy; // imagens do jME ficam verticalmente invertidas em relação ao AWT
            for (int x = 0; x < size; x++) {
                int sx = x * srcW / size;
                ColorRGBA c = raster.getPixel(sx, flippedY);
                thumb.setRGB(x, y, colorToArgb(c));
            }
        }
        return thumb;
    }

    /**
     * Carrega a textura escolhida pelo usuário e aplica ao slot indicado
     * (ColorMap, NormalMap, DiffuseMap, SpecularMap ou AmbientMap) do material
     * original (mtl) correspondente. Como todos os Geometry que compartilham
     * o mesmo material original apontam para a MESMA instância de Material,
     * a mudança é aplicada automaticamente a todas as partes do modelo que
     * usam esse material — sem precisar percorrer a hierarquia novamente.
     */
    public void setMaterialTexture(String materialName, String slot, File textureFile,
                                   Runnable onSuccess, Consumer<String> onError) {
        enqueue(() -> {
            try {
                Material mat = namedMaterials.get(materialName);
                if (mat == null) {
                    if (onError != null) onError.accept("Material não encontrado: " + materialName);
                    return;
                }
                if (mat.getMaterialDef().getMaterialParam(slot) == null) {
                    if (onError != null) onError.accept("Este material não suporta o slot " + slot);
                    return;
                }

                String folderPath = textureFile.getParentFile().getAbsolutePath();
                if (registeredTextureLocators.add(folderPath)) {
                    assetManager.registerLocator(folderPath, FileLocator.class);
                }
                assetManager.clearCache(); // garante que uma textura de mesmo nome usada antes não seja reaproveitada do cache

                Texture tex = assetManager.loadTexture(textureFile.getName());
                tex.setWrap(Texture.WrapMode.Repeat);
                mat.setTexture(slot, tex);

                if (onSuccess != null) onSuccess.run();
            } catch (Exception ex) {
                ex.printStackTrace();
                if (onError != null) onError.accept(ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        });
    }

    private BufferedImage createColorThumbnail(ColorRGBA color, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        int argb = colorToArgb(color);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                img.setRGB(x, y, argb);
            }
        }
        return img;
    }

    private int colorToArgb(ColorRGBA c) {
        int a = clamp255(c.a);
        int r = clamp255(c.r);
        int g = clamp255(c.g);
        int b = clamp255(c.b);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private int clamp255(float value) {
        return Math.max(0, Math.min(255, Math.round(value * 255f)));
    }

    /**
     * Isola visualmente um material: tudo que NÃO pertence a ele fica quase
     * transparente, e o que pertence fica normal. Passar null remove o
     * destaque, restaurando a opacidade original de tudo.
     * <p>
     * Limitação conhecida: no estilo "Caneta (Pen)", o passo de profundidade
     * do modelo já é invisível por natureza (ColorWrite desativado), então
     * o destaque de material não produz efeito visual nesse estilo específico.
     */
    public void setMaterialHighlight(String materialName, Runnable onDone) {
        enqueue(() -> {
            this.highlightedMaterialName = materialName;
            applyMaterialHighlightRecursive(modelRoot);
            if (onDone != null) onDone.run();
        });
    }

    private void applyMaterialHighlightRecursive(Spatial spatial, Set<Material> processedMaterials) {
        if (spatial instanceof Geometry geom) {
            Object nameData = geom.getUserData(USERDATA_ORIGINAL_MATERIAL_NAME);
            String geomMaterialName = (nameData instanceof String s) ? s : null;

            boolean shouldDim = highlightedMaterialName != null
                    && !highlightedMaterialName.equals(geomMaterialName);

            setGeometryDimmed(geom, shouldDim, processedMaterials);
        } else if (spatial instanceof Node node) {
            for (Spatial child : node.getChildren()) {
                applyMaterialHighlightRecursive(child, processedMaterials);
            }
        }
    }

    private void setGeometryDimmed(Geometry geom, boolean dim, Set<Material> processedMaterials) {
        Material mat = geom.getMaterial();
        if (mat == null) return;

        // Só muta o Material se ele ainda não foi processado nesta passada —
        // essencial para materiais compartilhados entre várias Geometry
        if (processedMaterials.add(mat)) {
            applyDimToMaterial(mat, dim);
        }

        // QueueBucket é individual por Geometry, então sempre ajustado aqui
        if (dim) {
            originalBucketByGeometry.putIfAbsent(geom, geom.getQueueBucket());
            geom.setQueueBucket(Bucket.Transparent);
        } else {
            Bucket originalBucket = originalBucketByGeometry.remove(geom);
            geom.setQueueBucket(originalBucket != null ? originalBucket : Bucket.Opaque);
        }
    }

    private void applyDimToMaterial(Material mat, boolean dim) {
        String colorParamName = mat.getParam("Diffuse") != null ? "Diffuse"
                : (mat.getParam("Color") != null ? "Color" : null);
        if (colorParamName == null) return;

        MatParam colorMatParam = mat.getParam(colorParamName);
        if (!(colorMatParam.getValue() instanceof ColorRGBA currentColor)) return;

        if (dim) {
            originalAlphaByMaterial.putIfAbsent(mat, currentColor.a);
            originalBlendByMaterial.putIfAbsent(mat, mat.getAdditionalRenderState().getBlendMode());

            ColorRGBA dimmedColor = currentColor.clone();
            dimmedColor.a = DIMMED_ALPHA;
            mat.setColor(colorParamName, dimmedColor);
            mat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        } else {
            Float originalAlpha = originalAlphaByMaterial.remove(mat);
            RenderState.BlendMode originalBlend = originalBlendByMaterial.remove(mat);

            if (originalAlpha != null) {
                ColorRGBA restoredColor = currentColor.clone();
                restoredColor.a = originalAlpha;
                mat.setColor(colorParamName, restoredColor);
                mat.getAdditionalRenderState().setBlendMode(
                        originalBlend != null ? originalBlend : BlendMode.Off);
            }
        }
    }

    /**
     * Remove a textura atualmente definida em um slot específico do material,
     * voltando esse canal ao estado "sem textura" (o material passa a usar
     * só a cor/parâmetro base daquele canal, se houver).
     */
    public void clearMaterialTexture(String materialName, String slot,
                                     Runnable onSuccess, Consumer<String> onError) {
        enqueue(() -> {
            try {
                Material mat = namedMaterials.get(materialName);
                if (mat == null) {
                    if (onError != null) onError.accept("Material não encontrado: " + materialName);
                    return;
                }
                if (mat.getMaterialDef().getMaterialParam(slot) == null) {
                    if (onError != null) onError.accept("Este material não suporta o slot " + slot);
                    return;
                }

                mat.clearParam(slot);

                if (onSuccess != null) onSuccess.run();
            } catch (Exception ex) {
                ex.printStackTrace();
                if (onError != null) onError.accept(ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        });
    }

    /**
     * Extrai as coordenadas UV de todas as Geometries que usam o material
     * indicado, retornando uma lista de arestas (cada uma como {u0,v0,u1,v1})
     * representando os três lados de cada triângulo em espaço UV (0.0 a 1.0).
     */
    public void requestUVLayout(String materialName, Consumer<java.util.List<float[]>> callback) {
        enqueue(() -> {
            java.util.List<Geometry> geoms = materialGroups.get(materialName);
            java.util.List<float[]> edges = new java.util.ArrayList<>();
            if (geoms != null) {
                for (Geometry geom : geoms) {
                    collectUVEdges(geom.getMesh(), edges);
                }
            }
            if (callback != null) callback.accept(edges);
        });
    }

    /**
     * Gera uma prévia da textura difusa do material em resolução maior que a
     * miniatura da lista (útil como fundo do editor de UV). Retorna null se
     * o material não tiver textura difusa.
     */
    public void requestMaterialTexturePreview(String materialName, int maxSize, Consumer<BufferedImage> callback) {
        enqueue(() -> {
            Material mat = namedMaterials.get(materialName);
            BufferedImage preview = null;

            if (mat != null) {
                MatParamTexture texParam = mat.getTextureParam("DiffuseMap");
                if (texParam == null) texParam = mat.getTextureParam("ColorMap");

                if (texParam != null && texParam.getTextureValue() instanceof Texture2D tex2D) {
                    try {
                        preview = createTextureThumbnail(tex2D, maxSize);
                    } catch (Exception ex) {
                        logger.log(Level.WARNING, "Falha ao gerar prévia da textura de {0}", materialName);
                    }
                }
            }
            BufferedImage finalPreview = preview;
            if (callback != null) callback.accept(finalPreview);
        });
    }

    private void collectUVEdges(Mesh mesh, java.util.List<float[]> edges) {
        FloatBuffer tcBuf = mesh.getFloatBuffer(Type.TexCoord);
        IndexBuffer idxBuf = mesh.getIndexBuffer();
        if (tcBuf == null || idxBuf == null) return; // malha sem UV (ex: sem textura mapeada)

        int triCount = mesh.getTriangleCount();
        for (int t = 0; t < triCount; t++) {
            int i0 = idxBuf.get(t * 3);
            int i1 = idxBuf.get(t * 3 + 1);
            int i2 = idxBuf.get(t * 3 + 2);

            float u0 = tcBuf.get(i0 * 2), v0 = tcBuf.get(i0 * 2 + 1);
            float u1 = tcBuf.get(i1 * 2), v1 = tcBuf.get(i1 * 2 + 1);
            float u2 = tcBuf.get(i2 * 2), v2 = tcBuf.get(i2 * 2 + 1);

            edges.add(new float[]{u0, v0, u1, v1});
            edges.add(new float[]{u1, v1, u2, v2});
            edges.add(new float[]{u2, v2, u0, v0});
        }
    }

    public ToolMode getToolMode() {
        return toolMode;
    }

    public void setToolMode(ToolMode mode) {
        this.toolMode = mode;
    }

    public RenderManager getRenderManager() {
        return renderManager;
    }

    public Node getRootNode() {
        return rootNode;
    }

    public Camera getCamera() {
        return cam;
    }

    public ViewPort getViewPort() {
        return viewPort;
    }

    public Node getGridNode() {
        return gridNode;
    }

    public Renderer getRenderer() {
        return renderer;
    }

    public File getLastLoadedObjFile() {
        return lastLoadedObjFile;
    }

    public enum ToolMode {ORBIT, ZOOM, PAN}

    public enum RenderStyle {FLAT_GRAY, ORIGINAL_MATERIAL, PEN, MONOCHROME}

    //cameras
    public enum CameraMode {FREE, FRONT, BACK, LEFT, RIGHT, TOP, BOTTOM}

    private static class EdgeData {
        final int v0, v1;
        int faceCount = 0;
        Vector3f normalA;
        Vector3f normalB;

        EdgeData(int v0, int v1) {
            this.v0 = v0;
            this.v1 = v1;
        }

        void addFace(Vector3f normal) {
            if (faceCount == 0) normalA = normal;
            else if (faceCount == 1) normalB = normal;
            faceCount++;
        }
    }
}