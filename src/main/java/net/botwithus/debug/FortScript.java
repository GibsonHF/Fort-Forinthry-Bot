package net.botwithus.debug;

import net.botwithus.api.game.hud.inventories.Backpack;
import net.botwithus.internal.scripts.ScriptDefinition;
import net.botwithus.rs3.events.impl.SkillUpdateEvent;
import net.botwithus.rs3.game.*;
import net.botwithus.rs3.game.hud.interfaces.Interfaces;
import net.botwithus.rs3.game.minimenu.MiniMenu;
import net.botwithus.rs3.game.minimenu.actions.ComponentAction;
import net.botwithus.rs3.game.movement.Movement;
import net.botwithus.rs3.game.movement.NavPath;
import net.botwithus.rs3.game.movement.TraverseEvent;
import net.botwithus.rs3.game.queries.builders.objects.SceneObjectQuery;
import net.botwithus.rs3.game.queries.results.EntityResultSet;
import net.botwithus.rs3.game.scene.entities.characters.player.Player;
import net.botwithus.rs3.game.scene.entities.object.SceneObject;
import net.botwithus.rs3.game.skills.Skills;
import net.botwithus.rs3.script.Execution;
import net.botwithus.rs3.script.LoopingScript;
import net.botwithus.rs3.script.config.ScriptConfig;
import net.botwithus.rs3.util.RandomGenerator;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FortScript extends LoopingScript {
    public boolean ActivePlay = true;
    public int totalTasks;
    private boolean hasSubmittedPlan;
    boolean isCurrentlyBuilding = false;

    public FortScript(String name, ScriptConfig scriptConfig, ScriptDefinition scriptDefinition) {
        super(name, scriptConfig, scriptDefinition);
    }

    public long startTime;
    public int runCounter = 0;

    public Area.Rectangular area = new Area.Rectangular(new Coordinate(3289, 3553, 0), new Coordinate(3293, 3556, 0));
    public boolean runScript = false;

    int xpGained = 0;
    int levelsGained = 0;

    private boolean hasClickedBuild = false;
    public String[] arrayOfString = {
            "Workshop (Tier 1)", "Workshop (Tier 2)", "Workshop (Tier 3)",
            "Town hall (Tier 1)", "Town hall (Tier 2)", "Town hall (Tier 3)",
            "Chapel (Tier 1)", "Chapel (Tier 2)", "Chapel (Tier 3)",
            "Command centre (Tier 1)", "Command centre (Tier 2)", "Command centre (Tier 3)",
            "Kitchen (Tier 1)", "Kitchen (Tier 2)", "Kitchen (Tier 3)",
            "Guardhouse (Tier 1)", "Guardhouse (Tier 2)", "Guardhouse (Tier 3)",
            "Grove cabin (Tier 1)", "Grove cabin (Tier 2)", "Grove cabin (Tier 3)",
            "Rangers workroom (Tier 1)", "Rangers workroom (Tier 2)", "Rangers workroom (Tier 3)",
            "Botanist's Workbench (Tier 1)", "Botanist's Workbench (Tier 2)", "Botanist's Workbench (Tier 3)"
    };

    public enum BotState {
        WALK_TO_CONSTRUCTION_SPOT,
        FIND_CONSTRUCTION_SPOT,
        FIND_BLUEPRINT_SPOT,
        WALK_TO_TABLE,
        CHECK_PLANS,
        CONTINUE_CURRENT_BUILDING
    }
    public BotState currentState = BotState.FIND_CONSTRUCTION_SPOT;
    public int selectedIndex = 0;

    public Pattern framePattern = Pattern.compile(".*frame.*", Pattern.CASE_INSENSITIVE);
    public Pattern wallSegmentPattern = Pattern.compile(".*wall segment.*", Pattern.CASE_INSENSITIVE);

    public List<String> taskQueue = new ArrayList<>();
    public boolean repeatQueue = false;
    public int currentTaskIndex = 0;
    public int[] taskCount = new int[arrayOfString.length];
    private String lastCompletedTask = null;

    @Override
    public boolean initialize() {
        this.sgc = new GraphicsContext(getConsole(), this);
        this.loopDelay = 550;

        startTime = System.currentTimeMillis();
        currentTaskIndex = 0;

        subscribe(SkillUpdateEvent.class, skillUpdateEvent -> {
            if (skillUpdateEvent.getId() == Skills.CONSTRUCTION.getId()) {
                xpGained += (skillUpdateEvent.getExperience() - skillUpdateEvent.getOldExperience());
                if (skillUpdateEvent.getOldActualLevel() < skillUpdateEvent.getActualLevel())
                    levelsGained++;
            }
        });

        return super.initialize();
    }

    @Override
    public void onLoop() {
        if (!runScript) {
            return;
        }
        Player player = Client.getLocalPlayer();
        if (Client.getGameState() != Client.GameState.LOGGED_IN || player == null) {
            Execution.delay(RandomGenerator.nextInt(2500, 5500));
            return;
        }

        SceneObject constructionHotspot = SceneObjectQuery.newQuery().name("Optimal Construction hotspot").results().first();
        if (constructionHotspot != null && isCurrentlyBuilding) {
            currentState = BotState.CONTINUE_CURRENT_BUILDING;
        } else if (taskQueue.isEmpty() && !isCurrentlyBuilding) {
            if (repeatQueue && lastCompletedTask != null) {
                taskQueue.addAll(Arrays.asList(arrayOfString));
                currentTaskIndex = 0;
                println("Queue was empty, refilled with all tasks due to repeatQueue setting");
            } else {
                println("Task queue is empty and not currently building. Stopping script.");
                runScript = false;
                return;
            }
        }

        if (!taskQueue.isEmpty() && currentState != BotState.CONTINUE_CURRENT_BUILDING) {
            String currentTask = taskQueue.get(currentTaskIndex);
            selectedIndex = Arrays.asList(arrayOfString).indexOf(currentTask);
            if (selectedIndex < 0) {
                println("Warning: Invalid task in queue: " + currentTask);
                taskQueue.remove(currentTaskIndex);
                if (currentTaskIndex >= taskQueue.size() && !taskQueue.isEmpty()) {
                    currentTaskIndex = 0;
                }
                return;
            }
        }

        switch (currentState) {
            case WALK_TO_CONSTRUCTION_SPOT:
                walkToConstructionSpot();
                break;
            case FIND_CONSTRUCTION_SPOT:
                findConstructionSpot();
                break;
            case FIND_BLUEPRINT_SPOT:
                findBlueprintSpot();
                break;
            case WALK_TO_TABLE:
                walkToTable();
                break;
            case CHECK_PLANS:
                checkPlans();
                if (hasSubmittedPlan) {
                    isCurrentlyBuilding = true;
                    hasSubmittedPlan = false;
                }
                break;
            case CONTINUE_CURRENT_BUILDING:
                continueCurrentBuilding();
                break;
        }
    }

    private void continueCurrentBuilding() {
        SceneObject spot = SceneObjectQuery.newQuery().name("Optimal Construction hotspot").results().first();
        if (spot != null) {
            handleSpot(spot);
        } else {
            println("Building completed successfully");
            isCurrentlyBuilding = false;

            if (!taskQueue.isEmpty()) {
                lastCompletedTask = taskQueue.get(currentTaskIndex);

                if (repeatQueue) {
                    int arrIndex = selectedIndex;
                    taskCount[arrIndex]++;
                    currentTaskIndex = (currentTaskIndex + 1) % taskQueue.size();
                } else {
                    int arrIndex = selectedIndex;
                    taskCount[arrIndex] = 0;
                    taskQueue.remove(currentTaskIndex);
                    if (currentTaskIndex >= taskQueue.size() && !taskQueue.isEmpty()) {
                        currentTaskIndex = 0;
                    }
                }
            }

            if (!taskQueue.isEmpty()) {
                currentState = BotState.WALK_TO_TABLE;
            } else if (repeatQueue) {
                taskQueue.addAll(Arrays.asList(arrayOfString));
                currentTaskIndex = 0;
                currentState = BotState.WALK_TO_TABLE;
            } else {
                println("No more tasks in queue. Stopping script.");
                runScript = false;
            }
        }
    }

    private void checkPlans() {
        if (taskQueue.isEmpty() && !isCurrentlyBuilding) {
            println("Task queue is empty");
            SceneObject hotspot = SceneObjectQuery.newQuery().name("Optimal Construction hotspot").results().first();
            if (hotspot != null) {
                isCurrentlyBuilding = true;
                currentState = BotState.CONTINUE_CURRENT_BUILDING;
            } else {
                println("No construction hotspot found and queue is empty. Stopping script.");
                runScript = false;
            }
            return;
        }

        if (!isCurrentlyBuilding && !taskQueue.isEmpty()) {
            String currentBuilding = taskQueue.get(currentTaskIndex);
            if (!hasRequiredMaterials(currentBuilding)) {
                println("Missing required materials for " + currentBuilding);
                currentState = BotState.FIND_BLUEPRINT_SPOT;
                return;
            }
        }

        if (Interfaces.isOpen(1370)) {
            int optionIndex = 1 + (selectedIndex * 4);
            MiniMenu.interact(ComponentAction.COMPONENT.getType(), 1, optionIndex, 89849878);
            Execution.delay(RandomGenerator.nextInt(800, 1500));
            MiniMenu.interact(ComponentAction.DIALOGUE.getType(), 0, -1, 89784350);
            Execution.delay(RandomGenerator.nextInt(3000, 5000));
            hasSubmittedPlan = true;
            currentState = BotState.WALK_TO_CONSTRUCTION_SPOT;
        }
    }

    private boolean hasRequiredMaterials(String building) {
        Map<String, Integer> requiredItems = BUILDING_REQUIREMENTS.get(building);
        if (requiredItems == null) {
            println("No material requirements found for: " + building);
            return false;
        }

        Map<String, Integer> inventoryCount = new HashMap<>();
        for (Item item : Backpack.getItems()) {
            String name = item.getName();
            if (name != null) {
                inventoryCount.put(name, inventoryCount.getOrDefault(name, 0) + item.getStackSize());
            }
        }

        for (Map.Entry<String, Integer> entry : requiredItems.entrySet()) {
            String requiredName = entry.getKey();
            int requiredAmount = entry.getValue();
            int haveAmount = inventoryCount.getOrDefault(requiredName, 0);

            if (haveAmount < requiredAmount) {
                println("Missing " + requiredName + " for " + building
                        + ". Required: " + requiredAmount + ", Found: " + haveAmount);
                return false;
            }
        }
        return true;
    }

    public Map<String, Integer> computeTotalMaterialsNeededForQueue() {
        Map<String, Integer> totalNeeded = new HashMap<>();

        for (String building : taskQueue) {
            Map<String, Integer> requiredItems = BUILDING_REQUIREMENTS.get(building);
            if (requiredItems != null) {
                for (Map.Entry<String, Integer> e : requiredItems.entrySet()) {
                    totalNeeded.merge(e.getKey(), e.getValue(), Integer::sum);
                }
            }
        }
        return totalNeeded;
    }

    private void walkToTable() {
        EntityResultSet<SceneObject> blueprintSpots = SceneObjectQuery.newQuery().name("Fort Forinthry blueprints").results();
        SceneObject spot = blueprintSpots.first();

        if (spot != null && Client.getLocalPlayer() != null && Distance.to(spot) <= 20) {
            // We're already close enough to interact directly
            println("Already close to blueprint table, proceeding to interact");
            currentState = BotState.FIND_BLUEPRINT_SPOT;
        } else if (!this.area.contains(Client.getLocalPlayer().getCoordinate())) {
            WalkTo(area.getRandomCoordinate());
        } else {
            println("Walking to table");
            currentState = BotState.FIND_BLUEPRINT_SPOT;
        }
    }

    private void findBlueprintSpot() {
        EntityResultSet<SceneObject> blueprintSpots = SceneObjectQuery.newQuery().name("Fort Forinthry blueprints").results();
        SceneObject spot = blueprintSpots.first();
        if (spot != null) {
            if (Client.getLocalPlayer() != null && !Client.getLocalPlayer().isMoving() && Distance.to(spot) <= 8) {
                runCounter++;
                boolean interfaceOpened = Execution.delayUntil(RandomGenerator.nextInt(200, 600), () -> Interfaces.isOpen(1370));

                if (interfaceOpened) {
                    println("Interface opened");
                    currentState = BotState.CHECK_PLANS;
                } else {
                    spot.interact("Check plans");
                    Execution.delay(RandomGenerator.nextInt(800, 1500));
                    println("Found blueprint spot");
                }
            }
        } else {
            SceneObject constructionSpot = SceneObjectQuery.newQuery().name("Optimal Construction hotspot").results().first();
            if (constructionSpot != null) {
                isCurrentlyBuilding = true;
                currentState = BotState.CONTINUE_CURRENT_BUILDING;
            } else {
                println("No blueprint spot or construction spot found. Stopping script.");
                runScript = false;
            }
        }
    }

    private void findConstructionSpot() {
        SceneObject spot = SceneObjectQuery.newQuery().name("Optimal Construction hotspot").results().first();
        if (spot != null) {
            isCurrentlyBuilding = true;
            handleSpot(spot);
        } else {
            isCurrentlyBuilding = false;
            currentState = BotState.WALK_TO_TABLE;
        }
    }

    private void handleSpot(SceneObject spot) {
        int distance = (int) Distance.to(spot);
        if (distance >= 20) {
            hasClickedBuild = false;
            currentState = BotState.WALK_TO_CONSTRUCTION_SPOT;
            println("Construction spot too far away (" + distance + " tiles), moving closer");
        } else if (!Client.getLocalPlayer().isMoving()) {
            interactWithSpot(spot);
        }
    }

    private void interactWithSpot(SceneObject spot) {
        if (!hasClickedBuild && Distance.to(spot) >= 1) {
            spot.interact("Build");
            println("Found construction spot");
            hasClickedBuild = true;
            Execution.delay(getDelay());
        } else if (hasClickedBuild && Distance.to(spot) > 1) {
            Execution.delay(getDelay());
            hasClickedBuild = false;
        }
    }

    private int getDelay() {
        return ActivePlay ? RandomGenerator.nextInt(200, 400) : RandomGenerator.nextInt(3000, 10000);
    }

    private void walkToConstructionSpot() {
        EntityResultSet<SceneObject> constructionSpots = SceneObjectQuery.newQuery().name("Optimal Construction hotspot").results();
        SceneObject spot = constructionSpots.first();

        if (spot != null) {
            Area.Rectangular area = new Area.Rectangular(spot.getCoordinate().derive(-2, -2, 0), spot.getCoordinate().derive(2, 2, 0));
            Coordinate randomCoordinate = area.getRandomCoordinate();

            if (Distance.to(spot) >= 20) {
                WalkTo(randomCoordinate);
                println("Walking to construction spot, distance: " + Distance.to(spot));
            } else {
                println("Hotspot closer than 20 tiles, clicking");
                currentState = BotState.FIND_CONSTRUCTION_SPOT;
            }
        } else {
            isCurrentlyBuilding = false;
            currentState = BotState.WALK_TO_TABLE;
        }
    }

    public boolean WalkTo(Coordinate coordinate) {
        if (Movement.traverse(NavPath.resolve(coordinate)) == TraverseEvent.State.FINISHED) {
            println("Arrived at " + coordinate.getX() + ", " + coordinate.getY());
            return true;
        } else {
            println("Failed to arrive at " + coordinate.getX() + ", " + coordinate.getY());
            return false;
        }
    }

    public boolean WalkTo(int x, int y) {
        Area targetArea = new Area.Rectangular(new Coordinate(x - 1, y - 1, 0), new Coordinate(x + 1, y + 1, 0));
        if (Movement.traverse(NavPath.resolve(targetArea)) == TraverseEvent.State.FINISHED) {
            println("Arrived at " + x + ", " + y);
            return true;
        } else {
            println("Failed to arrive at " + x + ", " + y);
            return false;
        }
    }

    public String getElapsedTime() {
        long endTime = System.currentTimeMillis();
        long elapsedTime = endTime - startTime;
        long hours = TimeUnit.MILLISECONDS.toHours(elapsedTime);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedTime) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedTime) % 60;
        return String.format("Elapsed time: %02d:%02d:%02d", hours, minutes, seconds);
    }

    private static final Map<String, Map<String, Integer>> BUILDING_REQUIREMENTS = new HashMap<>();

    static {
        Map<String, Integer> workshopT1 = new HashMap<>();
        workshopT1.put("Wooden frame", 8);
        workshopT1.put("Stone wall segment", 6);
        BUILDING_REQUIREMENTS.put("Workshop (Tier 1)", workshopT1);

        Map<String, Integer> workshopT2 = new HashMap<>();
        workshopT2.put("Teak frame", 20);
        workshopT2.put("Stone wall segment", 6);
        BUILDING_REQUIREMENTS.put("Workshop (Tier 2)", workshopT2);

        Map<String, Integer> workshopT3 = new HashMap<>();
        workshopT3.put("Yew frame", 48);
        workshopT3.put("Stone wall segment", 6);
        BUILDING_REQUIREMENTS.put("Workshop (Tier 3)", workshopT3);

        Map<String, Integer> townT1 = new HashMap<>();
        townT1.put("Oak frame", 10);
        townT1.put("Stone wall segment", 6);
        BUILDING_REQUIREMENTS.put("Town hall (Tier 1)", townT1);

        Map<String, Integer> townT2 = new HashMap<>();
        townT2.put("Maple frame", 22);
        townT2.put("Stone wall segment", 6);
        BUILDING_REQUIREMENTS.put("Town hall (Tier 2)", townT2);

        Map<String, Integer> townT3 = new HashMap<>();
        townT3.put("Magic frame", 60);
        townT3.put("Stone wall segment", 6);
        BUILDING_REQUIREMENTS.put("Town hall (Tier 3)", townT3);

        Map<String, Integer> chapelT1 = new HashMap<>();
        chapelT1.put("Oak frame", 10);
        chapelT1.put("Stone wall segment", 6);
        BUILDING_REQUIREMENTS.put("Chapel (Tier 1)", chapelT1);

        Map<String, Integer> chapelT2 = new HashMap<>();
        chapelT2.put("Acadia frame", 24);
        chapelT2.put("Stone wall segment", 6);
        BUILDING_REQUIREMENTS.put("Chapel (Tier 2)", chapelT2);

        Map<String, Integer> chapelT3 = new HashMap<>();
        chapelT3.put("Elder frame", 50);
        chapelT3.put("Stone wall segment", 6);
        BUILDING_REQUIREMENTS.put("Chapel (Tier 3)", chapelT3);

        Map<String, Integer> commandT1 = new HashMap<>();
        commandT1.put("Willow frame", 12);
        commandT1.put("Stone wall segment", 6);
        BUILDING_REQUIREMENTS.put("Command centre (Tier 1)", commandT1);

        Map<String, Integer> commandT2 = new HashMap<>();
        commandT2.put("Yew frame", 26);
        commandT2.put("Stone wall segment", 6);
        BUILDING_REQUIREMENTS.put("Command centre (Tier 2)", commandT2);

        Map<String, Integer> commandT3 = new HashMap<>();
        commandT3.put("Elder frame", 80);
        commandT3.put("Stone wall segment", 6);
        BUILDING_REQUIREMENTS.put("Command centre (Tier 3)", commandT3);

        Map<String, Integer> kitchenT1 = new HashMap<>();
        kitchenT1.put("Willow frame", 12);
        kitchenT1.put("Stone wall segment", 6);
        BUILDING_REQUIREMENTS.put("Kitchen (Tier 1)", kitchenT1);

        Map<String, Integer> kitchenT2 = new HashMap<>();
        kitchenT2.put("Acadia frame", 22);
        kitchenT2.put("Stone wall segment", 6);
        BUILDING_REQUIREMENTS.put("Kitchen (Tier 2)", kitchenT2);

        Map<String, Integer> kitchenT3 = new HashMap<>();
        kitchenT3.put("Magic frame", 50);
        kitchenT3.put("Stone wall segment", 6);
        BUILDING_REQUIREMENTS.put("Kitchen (Tier 3)", kitchenT3);

        Map<String, Integer> guardT1 = new HashMap<>();
        guardT1.put("Maple frame", 14);
        guardT1.put("Stone wall segment", 6);
        BUILDING_REQUIREMENTS.put("Guardhouse (Tier 1)", guardT1);

        Map<String, Integer> guardT2 = new HashMap<>();
        guardT2.put("Mahogany frame", 26);
        guardT2.put("Stone wall segment", 6);
        BUILDING_REQUIREMENTS.put("Guardhouse (Tier 2)", guardT2);

        Map<String, Integer> guardT3 = new HashMap<>();
        guardT3.put("Elder frame", 70);
        guardT3.put("Stone wall segment", 6);
        BUILDING_REQUIREMENTS.put("Guardhouse (Tier 3)", guardT3);

        Map<String, Integer> groveT1 = new HashMap<>();
        groveT1.put("Wooden frame", 8);
        groveT1.put("Stone wall segment", 6);
        BUILDING_REQUIREMENTS.put("Grove cabin (Tier 1)", groveT1);

        Map<String, Integer> groveT2 = new HashMap<>();
        groveT2.put("Teak frame", 20);
        groveT2.put("Stone wall segment", 6);
        BUILDING_REQUIREMENTS.put("Grove cabin (Tier 2)", groveT2);

        Map<String, Integer> groveT3 = new HashMap<>();
        groveT3.put("Mahogany frame", 48);
        groveT3.put("Stone wall segment", 6);
        BUILDING_REQUIREMENTS.put("Grove cabin (Tier 3)", groveT3);

        Map<String, Integer> rangerT1 = new HashMap<>();
        rangerT1.put("Acadia frame", 14);
        rangerT1.put("Stone wall segment", 6);
        BUILDING_REQUIREMENTS.put("Rangers workroom (Tier 1)", rangerT1);

        Map<String, Integer> rangerT2 = new HashMap<>();
        rangerT2.put("Mahogany frame", 24);
        rangerT2.put("Stone wall segment", 6);
        BUILDING_REQUIREMENTS.put("Rangers workroom (Tier 2)", rangerT2);

        Map<String, Integer> rangerT3 = new HashMap<>();
        rangerT3.put("Magic frame", 42);
        rangerT3.put("Stone wall segment", 6);
        BUILDING_REQUIREMENTS.put("Rangers workroom (Tier 3)", rangerT3);

        Map<String, Integer> botanistT1 = new HashMap<>();
        botanistT1.put("Acadia frame", 4);
        botanistT1.put("Stone wall segment", 6);
        BUILDING_REQUIREMENTS.put("Botanist's Workbench (Tier 1)", botanistT1);

        Map<String, Integer> botanistT2 = new HashMap<>();
        botanistT2.put("Yew frame", 8);
        botanistT2.put("Stone wall segment", 6);
        BUILDING_REQUIREMENTS.put("Botanist's Workbench (Tier 2)", botanistT2);

        Map<String, Integer> botanistT3 = new HashMap<>();
        botanistT3.put("Elder frame", 12);
        botanistT3.put("Stone wall segment", 6);
        BUILDING_REQUIREMENTS.put("Botanist's Workbench (Tier 3)", botanistT3);
    }
}
