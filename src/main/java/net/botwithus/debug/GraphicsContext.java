package net.botwithus.debug;

import net.botwithus.rs3.game.skills.Skills;
import net.botwithus.rs3.imgui.ImGui;
import net.botwithus.rs3.script.ScriptConsole;
import net.botwithus.rs3.script.ScriptGraphicsContext;

import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Map;

public class GraphicsContext extends ScriptGraphicsContext {

    private final FortScript script;
    public int ConstructionXPGained = 0;
    public int ConstructionXPStart;

    public GraphicsContext(ScriptConsole console, FortScript script) {
        super(console);
        this.script = script;
        this.ConstructionXPStart = Skills.CONSTRUCTION.getSkill().getExperience();
    }

    @Override
    public void drawSettings() {
        // Increase window size for better readability
        ImGui.SetWindowSize(280.f, 400.f);
        if(ImGui.Begin("Fort Forinthry Builder", 0)) {
            // -- MAIN CONTROLS SECTION --
            drawMainControls();
            ImGui.Separator();

            // -- STATISTICS SECTION --
            drawStatistics();
            ImGui.Separator();

            // -- BUILDING OPTIONS SECTION --
            drawBuildingOptions();
            ImGui.Separator();

            // -- QUEUE MANAGEMENT SECTION --
            drawQueueManagement();

            // -- MATERIALS REQUIRED SECTION --
            if(!script.taskQueue.isEmpty()) {
                ImGui.Separator();
                drawMaterialsNeeded();
            }

            ImGui.End();
        }
    }

    private void drawMainControls() {
        script.runScript = ImGui.Checkbox("Run Script", script.runScript);
        ImGui.SameLine();
        script.ActivePlay = ImGui.Checkbox("Active Mode", script.ActivePlay);

        ImGui.Text("Bot State: " + script.currentState);

        // Show building status
        if (script.isCurrentlyBuilding) {
            ImGui.Text("Currently Building");
        }

        ImGui.Text("Click run script with materials in inventory.");
        ImGui.Text("Will navigate to Fort Forinthry if needed.");
    }

    private void drawStatistics() {
        if(ImGui.Button("Reset Construction XP")) {
            ConstructionXPGained = 0;
            script.xpGained = 0;
            ConstructionXPStart = Skills.CONSTRUCTION.getSkill().getExperience();
        }

        ImGui.Text("Run Counter: " + script.runCounter);
        ImGui.Text(script.getElapsedTime());
        ImGui.Text("Construction XP Gained: " + calculateConstructionXPGained() + " XP");
        ImGui.Text("Construction XP/Hour: " + calculateConstructionXPPerHour() + " XP");
        ImGui.Text("Levels Gained: " + script.levelsGained);
    }

    private void drawBuildingOptions() {
        if(ImGui.Button("Build Entire Fort From Scratch")) {
            script.taskQueue.clear();
            for (int i = 0; i < script.arrayOfString.length; i++) {
                script.taskQueue.add(script.arrayOfString[i]);
                script.totalTasks++;
            }
            script.currentTaskIndex = 0;
        }

        script.selectedIndex = ImGui.Combo("Building Type", script.selectedIndex, script.arrayOfString);
        ImGui.SameLine();
        if (ImGui.Button("Add to Queue")) {
            script.taskQueue.add(script.arrayOfString[script.selectedIndex]);
            script.totalTasks++;
            if (script.currentTaskIndex >= script.taskQueue.size()) {
                script.currentTaskIndex = 0;
            }
        }

        script.repeatQueue = ImGui.Checkbox("Repeat Queue When Complete", script.repeatQueue);
    }

    private void drawQueueManagement() {
        // Current task status
        if(!script.taskQueue.isEmpty()) {
            String currentTask = script.taskQueue.get(script.currentTaskIndex);
            ImGui.Text("Current Task: " + currentTask);

            int arrIndex = Arrays.asList(script.arrayOfString).indexOf(currentTask);
            ImGui.Text("Task Progress: (" + script.taskCount[arrIndex] + "/" + script.taskQueue.size() + ")");

            // Queue list with clear indication
            ImGui.Text("Queue: (Click to remove)");
            for (int i = 0; i < script.taskQueue.size(); i++) {
                String taskDisplay = (i == script.currentTaskIndex) ?
                    "-> " + (i + 1) + ". " + script.taskQueue.get(i) :
                    "   " + (i + 1) + ". " + script.taskQueue.get(i);

                if (ImGui.Selectable(taskDisplay, false, 0)) {
                    String removedTask = script.taskQueue.remove(i);
                    script.totalTasks--;
                    // Find the corresponding index in arrayOfString and decrement its count
                    int indexInArray = Arrays.asList(script.arrayOfString).indexOf(removedTask);
                    if (indexInArray >= 0) {
                        script.taskCount[indexInArray] = Math.max(script.taskCount[indexInArray] - 1, 0);
                    }
                    // Adjust currentTaskIndex if it goes out of bounds
                    if (script.currentTaskIndex >= script.taskQueue.size() && script.taskQueue.size() > 0) {
                        script.currentTaskIndex = 0;
                    }
                    break;  // Break to prevent concurrent modification issues
                }
            }

            // Clear queue button
            if(ImGui.Button("Clear Queue")) {
                script.taskQueue.clear();
                script.totalTasks = 0;
                script.currentTaskIndex = 0;
                Arrays.fill(script.taskCount, 0);
            }
        } else {
            ImGui.Text("No Tasks In Queue");
        }
    }

    private void drawMaterialsNeeded() {
        ImGui.Text("Materials needed for entire queue:");
        Map<String, Integer> needed = script.computeTotalMaterialsNeededForQueue();
        for (Map.Entry<String, Integer> e : needed.entrySet()) {
            ImGui.Text(e.getKey() + ": " + e.getValue());
        }
    }

    public String calculateConstructionXPPerHour() {
        int constructionXPGained = Skills.CONSTRUCTION.getSkill().getExperience() - ConstructionXPStart;
        long timeElapsedMillis = System.currentTimeMillis() - script.startTime;
        // Avoid division by very small numbers
        if (timeElapsedMillis < 1000) {
            return "0";
        }
        int xpPerHour = (int) (constructionXPGained / (timeElapsedMillis / 3600000.0));
        NumberFormat numberFormat = NumberFormat.getInstance();
        return numberFormat.format(xpPerHour);
    }

    public String calculateConstructionXPGained() {
        ConstructionXPGained = Skills.CONSTRUCTION.getSkill().getExperience() - ConstructionXPStart;
        NumberFormat numberFormat = NumberFormat.getInstance();
        return numberFormat.format(ConstructionXPGained);
    }

    @Override
    public void drawOverlay() {
        super.drawOverlay();
    }
}
