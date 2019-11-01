package model;

import com.comsol.model.Model;
import com.comsol.model.physics.PhysicsFeature;
import com.comsol.model.util.ModelUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

/**
 * model.ModelWrapper
 *
 * Master high-level class for managing a model, its metadata, and various critical operations such as creating parts
 * and extracting potentials. This class houses the "meaty" operations of actually interacting with the model object
 * when creating parts in the static class model.Parts.
 *
 * Up for consideration: where to house the meshing/solving/extracting code?
 */
public class ModelWrapper {

    // INSTANCE VARIABLES

    // main model
    private Model model;

    // top level identifier manager
    public IdentifierManager im = new IdentifierManager();

    // managing parts within COMSOL
    private HashMap<String, IdentifierManager> partPrimitiveIMs = new HashMap<>();

    // directory structure
    private String root;
    private String dest;


    // CONSTRUCTORS

    /**
     * Default constructor (minimum of 2 arguments)
     * @param model com.comsol.model.Model object is REQUIRED
     * @param projectRoot the root directory of the project (might remove if unnecessary)
     */
    ModelWrapper(Model model, String projectRoot) {
        this.model = model;
        this.root = projectRoot;
    }

    /**
     * Overloaded constructor for passing in save directory
     * @param model com.comsol.model.Model object is REQUIRED
     * @param projectRoot the root directory of the project (might remove if unnecessary)
     * @param defaultSaveDestination directory in which to save (relative to project root)
     */
    ModelWrapper(Model model, String projectRoot, String defaultSaveDestination) {
        this(model, projectRoot);
        this.dest = defaultSaveDestination;
    }


    // ACCESSOR/MUTATOR METHODS

    /**
     * @return the model
     */
    public Model getModel() {
        return model;
    }

    /**
     * @return the root of the project (String path)
     */
    public String getRoot() {
        return root;
    }

    /**
     * @return the destination path to which to save the model
     */
    public String getDest() {
        return dest;
    }

    /**
     * @param root set the project root (String path)
     */
    public void setRoot(String root) {
        this.root = root;
    }

    /**
     * @param dest set the destination path to which to save the model
     */
    public void setDest(String dest) {
        this.dest = dest;
    }

    // OTHER METHODS

    /**
     * call method on im (IdentifierManager)... see class for details
     */
    public String next(String key) {
        return this.im.next(key);
    }

    /**
     * call method on im (IdentifierManager)... see class for details
     */
    public String next(String key, String pseudonym) {
        return this.im.next(key, pseudonym);
    }

    /**
     * call method on im (IdentifierManager)... see class for details
     */
    public String get(String psuedonym) {
        return this.im.get(psuedonym);
    }

    /**
     * @param partPrimitiveLabel the name of the part primitive (i.e. "TubeCuff_Primitive")
     * @return the associated IdentifierManager, for correct intra-part indexing
     */
    public IdentifierManager getPartPrimitiveIM(String partPrimitiveLabel) {
        return this.partPrimitiveIMs.get(partPrimitiveLabel);
    }

    /**
     *
     * @param destination full path to save to
     * @return success indicator
     */
    public boolean save(String destination) {
        try {
            this.model.save(destination);
            return true;
        } catch (IOException e) {
            System.out.println("Failed to save to destination: " + destination);
            return false;
        }
    }

    /**
     * Convenience method for saving to relative directory (this.dest) wrt the project directory (root)
     * @return success indicator
     */
    public boolean save() {
        if (this.dest != null) return save(String.join("/", new String[]{this.root, this.dest}));
        else {
            System.out.println("Save directory not initialized");
            return false;
        }
    }

    /**
     * TODO: UNFINISHED
     * Examples of parts: cuff, fascicle, etc.
     * The method will automatically create required part primitives and pass the HashMap with their id's to model.Part
     * @param name the name of the JSON configuration (same as unique indicator) for a given part
     * @return success indicator (might remove this later)
     */

    // TRY to initialize the part (catch error if no existing implementation)
//            Part.createPartInstance(this.next("pi", name), name, this);

    public boolean addPartPrimitives(String name) {
        // extract data from json
        try {
            JSONObject data = new JSONReader(String.join("/",
                    new String[]{this.root, ".templates", name})).getData();

            // get the id for the next "par" (i.e. parameters section), and give it a name from the JSON file name
            String id = this.next("par", name);
            model.param().group().create(id);
            model.param(id).label(name.split("\\.")[0]);

            // loop through all parameters in file, and set in parameters
            for (Object item : (JSONArray) data.get("params")) {
                JSONObject itemObject = (JSONObject) item;

                model.param(id).set(
                        (String) itemObject.get("name"),
                        (String) itemObject.get("expression"),
                        (String) itemObject.get("description")
                );
            }

            // for each required part primitive, create it (if not already existing)
            for (Object item: (JSONArray) data.get("instances")) {
                JSONObject itemObject = (JSONObject) item;
                String partPrimitiveName = (String) itemObject.get("type"); // quick cast to String

                // create the part primitive if it has not already been created
                if (! this.im.hasPseudonym(partPrimitiveName)) {
                    // get next available (TOP LEVEL) "part" id
                    String partID = this.im.next("part", partPrimitiveName);
                    try {
                        // TRY to create the part primitive (catch error if no existing implementation)
                        IdentifierManager partPrimitiveIM = Part.createPartPrimitive(partID, partPrimitiveName, this);

                        // add the returned id manager to the HashMap of IMs with the partName as its key
                        this.partPrimitiveIMs.put(partPrimitiveName, partPrimitiveIM);

                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                        return false;
                    }
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public boolean addPartInstances(String name) {
        // extract data from json
        // name is something like Enteromedics.json
        try {
            JSONObject data = new JSONReader(String.join("/",
                    new String[]{this.root, ".templates", name})).getData();

            // loop through all part instances
            for (Object item: (JSONArray) data.get("instances")) {
                JSONObject itemObject = (JSONObject) item;

                String instanceLabel = (String) itemObject.get("label");
                String instanceID = this.im.next("pi", instanceLabel);
                String type = (String) itemObject.get("type");
                Part.createCuffPartInstance(instanceID, instanceLabel, type , this, itemObject);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    //
    public boolean addMaterialDefinitions(String name) {
        // extract data from json
        try {
            JSONObject data = new JSONReader(String.join("/",
                    new String[]{this.root, ".templates", name})).getData();

            JSONObject master = new JSONReader(String.join("/",
                    new String[]{this.root, ".config", "master.json"})).getData();

            // for each material definition, create it (if not already existing)
            for (Object item: (JSONArray) data.get("instances")) {
                JSONObject itemObject = (JSONObject) item;


                JSONArray materials = itemObject.getJSONArray("materials");

                for(Object o: materials) {
                    String materialName = ((JSONObject) o).getString("type");
                    // create the material definition if it has not already been created
                    if (! this.im.hasPseudonym(materialName)) {
                        // get next available (TOP LEVEL) "material" id
                        String materialID = this.im.next("mat", materialName);
                        try {
                            // TRY to create the material definition (catch error if no existing implementation)
                            Part.defineMaterial(materialID, materialName, master, this);
                        } catch (IllegalArgumentException e) {
                            e.printStackTrace();
                            return false;
                        }
                    }
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public boolean extractPotentials(String json_path) {

        // see todos below (unrelated to this method hahahah - HILARIOUS! ROFL!)
        // TODO: Simulation folders; sorting through configuration files VIA PYTHON
        // TODO: FORCE THE USER TO STAGE/COMMIT CHANGES BEFORE RUNNING; add Git Commit ID/number to config file
        try {
            JSONObject json_data = new JSONReader(String.join("/", new String[]{root, json_path})).getData();

            double[][] coordinates = new double[3][5];
            String id = this.next("interp");

            model.result().numerical().create(id, "Interp");
            model.result().numerical(id).set("expr", "V");
            model.result().numerical(id).setInterpolationCoordinates(coordinates);

            double[][][] data = model.result().numerical(id).getData();

            System.out.println("data.toString() = " + Arrays.deepToString(data));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public boolean addNerve() {
        return true;
    }

    /**
     *
     * @return
     */
    // TODO: add fascicle paths to mw so they can be accessed in parts
    public boolean addFascicles() {

        // define global part primitive names (MUST BE IDENTICAL IN Part)
        String[] partPrimitiveNames = new String[]{"FascicleCI", "FascicleMesh"};

        // loop through fascicle primitives and create in COMSOL
        for (String partPrimitiveName: partPrimitiveNames) {
            if (!this.im.hasPseudonym(partPrimitiveName)) {
                // TRY to create the part primitive (catch error if no existing implementation)
                IdentifierManager partPrimitiveIM = Part.createPartPrimitive(this.im.next("part", partPrimitiveName), partPrimitiveName, this);
                // add the returned id manager to the HashMap of IMs with the partName as its key
                this.partPrimitiveIMs.put(partPrimitiveName, partPrimitiveIM);
            }
        }

        try {
            JSONObject json_data = new JSONReader(String.join("/", new String[]{
                    this.root,
                    ".config",
                    "master.json"
            })).getData();

            String fasciclesPath = String.join("/", new String[]{
                    this.root,
                    "data",
                    "samples",
                    (String) json_data.get("sample"),
                    "0", // these 0's are temporary (for 3d models will need to change)
                    "0",
                    (String) ((JSONObject) json_data.get("modes")).get("write"),
                    "fascicles"
            });

            String[] dirs = new File(fasciclesPath).list();
            if (dirs != null) {
                int i = 0;
                for (String dir: dirs) {
                    if (! dir.contains(".")) {
                        String fascicleName = "fascicle" + (i++);

                        // this parameter is just for show/debugging purposes in the COMSOL GUI
                        // it is not actually used for primitive instantiation

                        model.param().set(fascicleName, "NaN", dir);

                        // initialize data to send to Part.createPartInstance
                        HashMap<String, String[]> data = new HashMap<>();

                        // add inners and outers paths to array
                        for (String type: new String[]{"inners", "outer"}) {
                            data.put(type,
                                    new File(
                                            String.join("/", new String[]{fasciclesPath, dir, type})
                                    ).list()
                            );
                        }

                        // quick loop to make sure there are at least one of each inner and outer
                        for (String[] arr: data.values()) {
                            if (arr.length < 1) throw new IllegalStateException("There must be at least one of each inner and outer for fascicle " + i);
                        }

                        // do FascicleCI if only one inner, FascicleMesh otherwise
                        String primitiveType = data.get("inners").length == 1 ? partPrimitiveNames[0] : partPrimitiveNames[1];

                        // hand off to Part to build instance of fascicle
                        Part.createCuffPartInstance(this.im.next("pi"), fascicleName, primitiveType,this, null, data);
                    }
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        return true;
    }

    public static void main(String[] args) {
        ModelUtil.connect("localhost", 2036);
        ModelUtil.initStandalone(false);
        Model model = ModelUtil.create("Model");
        model.component().create("comp1", true);
        model.component("comp1").geom().create("geom1", 3);
        model.component("comp1").physics().create("ec", "ConductiveMedia", "geom1");
        model.component("comp1").mesh().create("mesh1");

        String projectPath = args[0];
        ModelWrapper mw = new ModelWrapper(model, projectPath);

        String configFile = "/.config/master.json";
        JSONObject configData = null;
        try {
            configData = new JSONReader(projectPath + configFile).getData();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        // Build medium

        // Read cuffs to build from master.json (cuff.preset) which links to JSON containing instantiations of parts
        // needed to build cuff (and fill)
        JSONObject cuffObject = (JSONObject) configData.get("cuff");
        JSONArray cuffs = (JSONArray) cuffObject.get("preset");
        ArrayList<String> cuffFiles = new ArrayList<>();

        // Build cuffs
        for (int i = 0; i < cuffs.length(); i++) {
            // make list of cuffs in model
            String cuff = cuffs.getString(i);

            // add part primitives needed to make the cuff
            mw.addPartPrimitives(cuff);

            // add material definitions needed to make the cuff
            mw.addMaterialDefinitions(cuff);

            // add part instances needed to make the cuff
            mw.addPartInstances(cuff);
        }

        // Build nerve
        mw.addFascicles();

        model.component("comp1").geom("geom1").run("fin");

        try {
            model.save("parts_test"); // TODO this dir needs to change
        } catch (IOException e) {
            e.printStackTrace();
        }

        mw.loopCurrents();
        mw.addFascicles();

        ModelUtil.disconnect();
        System.out.println("Disconnected from COMSOL Server");
    }

    public void loopCurrents() {
        for(String key: this.im.currentPointers.keySet()) {
            System.out.println("Current pointer: " + key);
            PhysicsFeature current = (PhysicsFeature) this.im.currentPointers.get(key);

            current.set("Qjp", 0.001);
        }
    }
}
