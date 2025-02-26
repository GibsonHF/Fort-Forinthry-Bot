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
        ImGui.SetWindowSize(200.f, 200.f);
        if(ImGui.Begin("Fort Forinthry Bot", 0)) {
            script.runScript = ImGui.Checkbox("Run Script", script.runScript);
            script.ActivePlay = ImGui.Checkbox("Active - Perfect builds", script.ActivePlay);
            ImGui.Separator();
            if(ImGui.Button("Reset Construction XP gained")) {
                ConstructionXPGained = 0;
                script.xpGained = 0;
                ConstructionXPStart = Skills.CONSTRUCTION.getSkill().getExperience();
            }
            ImGui.Text("Run Counter: " + script.runCounter);
            ImGui.Text(script.getElapsedTime());
            ImGui.Text("Construction XP Gained: " + calculateConstructionXPGained() + " XP");
            ImGui.Text("Construction XP Per Hour: " + calculateConstructionXPPerHour() + " XP");
            ImGui.Text("Levels Gained: " + script.levelsGained);
            ImGui.Separator();
            ImGui.Text("Bot State: " + script.currentState);
            ImGui.Separator();
            ImGui.Text("Click run script and have the required materials in your inventory.");
            ImGui.Text("Will navigate to Fort Forinthry if you aren't there.");
            if(ImGui.Button("Build entire Fort From Scratch")) {
                script.taskQueue.clear();
                for (int i = 0; i < script.arrayOfString.length; i++) {
                    script.taskQueue.add(script.arrayOfString[i]);
                    // totalTasks is updated here as each task is added
                    script.totalTasks++;
                }
                script.currentTaskIndex = 0;
            }
            ImGui.Separator();
            script.selectedIndex = ImGui.Combo("Construction Spot", script.selectedIndex, script.arrayOfString);
            ImGui.SameLine();
            if (ImGui.Button("Add to Queue")) {
                script.taskQueue.add(script.arrayOfString[script.selectedIndex]);
                script.totalTasks++; // Increment the total number of tasks
                script.currentTaskIndex = 0;
            }
            ImGui.Text("Queue:");
            // Display each task in the queue. Clicking on a task will remove it.
            for (int i = 0; i < script.taskQueue.size(); i++) {
                // The selectable's selected state is set if it is the last item (as in your original logic)
                if (ImGui.Selectable(script.taskQueue.get(i), script.taskQueue.get(i).equals(script.taskQueue.get(script.taskQueue.size() - 1)), 0)) {
                    String removedTask = script.taskQueue.remove(i);
                    script.totalTasks--; // Decrement the total number of tasks
                    // Find the corresponding index in arrayOfString and decrement its count
                    int arrIndex = Arrays.asList(script.arrayOfString).indexOf(removedTask);
                    script.taskCount[arrIndex] = Math.max(script.taskCount[arrIndex] - 1, 0);
                    // Adjust currentTaskIndex if it goes out of bounds
                    if (script.currentTaskIndex >= script.taskQueue.size()) {
                        script.currentTaskIndex = 0;
                    }
                    break;  // Break to prevent concurrent modification issues
                }
            }
            ImGui.Separator();
            if(!script.taskQueue.isEmpty()){
                ImGui.Text("Current Task: " + script.taskQueue.get(script.currentTaskIndex));
                int arrIndex = Arrays.asList(script.arrayOfString).indexOf(script.taskQueue.get(script.currentTaskIndex));
                ImGui.Text("Task Count: (" + script.taskCount[arrIndex] + "/" + script.taskQueue.size() + ")");
                ImGui.Separator();
                ImGui.Text("Total materials needed for entire queue:");
                Map<String, Integer> needed = script.computeTotalMaterialsNeededForQueue();
                for (Map.Entry<String, Integer> e : needed.entrySet()) {
                    ImGui.Text(e.getKey() + ": " + e.getValue());
                }

            } else {
                ImGui.Text("No Current Task");
            }
            ImGui.Separator();
            script.repeatQueue = ImGui.Checkbox("Repeat Queue", script.repeatQueue);

            ImGui.End();
        }
    }

    public String calculateConstructionXPPerHour() {
        int constructionXPGained = Skills.CONSTRUCTION.getSkill().getExperience() - ConstructionXPStart;
        long timeElapsedMillis = System.currentTimeMillis() - script.startTime;
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
