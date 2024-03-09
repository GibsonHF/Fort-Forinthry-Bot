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
import net.botwithus.rs3.imgui.NativeInteger;
import net.botwithus.rs3.script.Execution;
import net.botwithus.rs3.script.LoopingScript;
import net.botwithus.rs3.script.config.ScriptConfig;
import net.botwithus.rs3.util.RandomGenerator;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FortScript extends LoopingScript {
    public boolean ActivePlay = true;

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


    public enum BotState {
        WALK_TO_CONSTRUCTION_SPOT,
        FIND_CONSTRUCTION_SPOT,
        FIND_BLUEPRINT_SPOT,
        WALK_TO_TABLE,
        CHECK_PLANS
    }
    BotState currentState = BotState.FIND_CONSTRUCTION_SPOT;
    public NativeInteger selectedIndex = new NativeInteger(0);

    public  Pattern framePattern = Pattern.compile(".*frame.*", Pattern.CASE_INSENSITIVE);
    public Pattern wallSegmentPattern = Pattern.compile(".*wall segment.*", Pattern.CASE_INSENSITIVE);

    @Override
    public boolean initialize() {
        this.sgc = new GraphicsContext(getConsole(), this);
        this.loopDelay = 550;

        startTime = System.currentTimeMillis();

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
            Execution.delay(RandomGenerator.nextInt(2500,5500));
            return;
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
                break;
        }
    }
    private boolean hasRequiredMaterials() {

        int frameCount = 0;
        int wallSegmentCount = 0;

        for (Item item : Backpack.getItems()) {
            String itemName = item.getName();
            if(itemName == null) {
                continue;
            }

            Matcher frameMatcher = framePattern.matcher(itemName);
            if (frameMatcher.matches()) {
                frameCount++;
            }

            Matcher wallSegmentMatcher = wallSegmentPattern.matcher(itemName);
            if (wallSegmentMatcher.matches()) {
                wallSegmentCount++;
            }
        }

        boolean hasAllMaterials = frameCount > 0 && wallSegmentCount > 0;

        if (frameCount == 0) {
            println("Missing frame");
        }
        if (wallSegmentCount == 0) {
            println("Missing wall segment");
        }

        return hasAllMaterials;
    }
    private void checkPlans() {
        if (Interfaces.isOpen(1370)) {
            if(!hasRequiredMaterials()) {
                println("Missing required materials");
                currentState = BotState.FIND_BLUEPRINT_SPOT;
                runScript = false;
                return;
            }else
            {
            int optionIndex = 1 + (selectedIndex.get() * 4);
            MiniMenu.interact(ComponentAction.COMPONENT.getType(), 1, optionIndex, 89849878);
            Execution.delay(RandomGenerator.nextInt(800, 1500));
            MiniMenu.interact(ComponentAction.DIALOGUE.getType(), 0, -1, 89784350);
            Execution.delay(RandomGenerator.nextInt(3000, 5000));
            currentState = BotState.WALK_TO_CONSTRUCTION_SPOT;
            }
            }
    }

    private void walkToTable() {
        Coordinate table = area.getRandomCoordinate();
        if (!this.area.contains(Client.getLocalPlayer().getCoordinate())) {
            WalkTo(table);
        }else {
                println("Walking to table");
                currentState = BotState.FIND_BLUEPRINT_SPOT;
        }
    }

    private void findBlueprintSpot() {
        EntityResultSet<SceneObject> Blueprintspot = SceneObjectQuery.newQuery().name("Fort Forinthry blueprints").results();
        SceneObject spot = Blueprintspot.first();
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
            currentState = BotState.FIND_CONSTRUCTION_SPOT;
        }
    }

    private void findConstructionSpot() {
        EntityResultSet<SceneObject> constructionspot = SceneObjectQuery.newQuery().name("Optimal Construction hotspot").results();
        SceneObject spot = constructionspot.first();
        if (spot != null) {
            if(Distance.to(spot) >= 30)
            {
                hasClickedBuild = false;
                currentState = BotState.WALK_TO_CONSTRUCTION_SPOT;
            }else {
            if(!Client.getLocalPlayer().isMoving()) {
                if (!hasClickedBuild && Distance.to(spot) >= 1) {
                    spot.interact("Build");
                    println("Found construction spot");
                    hasClickedBuild = true;
                    if (ActivePlay) {
                        Execution.delay(RandomGenerator.nextInt(800, 1200));
                    } else {
                        Execution.delay(RandomGenerator.nextInt(3000, 10000));
                    }
                } else if (hasClickedBuild && Distance.to(spot) > 1) {
                    if (ActivePlay) {
                        Execution.delay(RandomGenerator.nextInt(800, 1200));
                    } else {
                        Execution.delay(RandomGenerator.nextInt(3000, 10000));
                    }
                    hasClickedBuild = false;
                }
                }
            }
        }
        else {
            hasClickedBuild = false;
            currentState = BotState.WALK_TO_TABLE;

        }
    }


    private void walkToConstructionSpot() {
        EntityResultSet<SceneObject> constructionSpots = SceneObjectQuery.newQuery().name("Optimal Construction hotspot").results();
        SceneObject spot = constructionSpots.first();

        if (spot != null) {
            Area.Rectangular area = new Area.Rectangular(spot.getCoordinate().derive(-2, -2, 0), spot.getCoordinate().derive(2, 2, 0));

            Coordinate randomCoordinate = area.getRandomCoordinate();


            if(Distance.to(spot) >= 30)
            {
                WalkTo(randomCoordinate);
                println("Walking to construction spot");
            }else {
                println("hotspot closer than 30, clicking");
                currentState = BotState.FIND_CONSTRUCTION_SPOT;
            }
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


        // Check if the player has arrived at the target area
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

}
