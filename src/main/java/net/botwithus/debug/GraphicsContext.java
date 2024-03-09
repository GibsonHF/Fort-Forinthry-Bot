package net.botwithus.debug;

import net.botwithus.rs3.game.skills.Skills;
import net.botwithus.rs3.imgui.ImGui;
import net.botwithus.rs3.script.ScriptConsole;
import net.botwithus.rs3.script.ScriptGraphicsContext;

import java.text.NumberFormat;
import java.util.Arrays;

public class GraphicsContext extends ScriptGraphicsContext {

    private final FortScript script;
    public int ConstructionXPGained = 0;
    public int ConstructionXPStart;

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
            ImGui.Combo("Construction Spot", script.selectedIndex, Arrays.copyOf(arrayOfString, arrayOfString.length));
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
            //ImGui.Text("XP Gained: " + script.xpGained);
            ImGui.Separator();
            ImGui.Text("Bot State: " + script.currentState);
            ImGui.Separator();
            ImGui.Text("Click run script and have the required materials in your inventory.");
            ImGui.Text("Will navigate to Fort forinthry if you arent there.");
            ImGui.End();
        }
    }

    public String calculateConstructionXPPerHour() {
        int ConstructionXPGained = Skills.CONSTRUCTION.getSkill().getExperience() - ConstructionXPStart;

        long timeElapsedMillis = System.currentTimeMillis() - script.startTime;

        int xpPerHour = (int) (ConstructionXPGained / (timeElapsedMillis / 3600000.0));

        NumberFormat numberFormat = NumberFormat.getInstance();
        String formattedXpPerHour = numberFormat.format(xpPerHour);

        return formattedXpPerHour;
    }

    public String calculateConstructionXPGained() {
        // Calculate the Construction XP gained
        ConstructionXPGained = Skills.CONSTRUCTION.getSkill().getExperience() - ConstructionXPStart;

        // Format the XP gained with commas for thousands and a decimal point
        NumberFormat numberFormat = NumberFormat.getInstance();
        String formattedXpGained = numberFormat.format(ConstructionXPGained);

        return formattedXpGained;
    }

    @Override
    public void drawOverlay() {
        super.drawOverlay();
    }
}
