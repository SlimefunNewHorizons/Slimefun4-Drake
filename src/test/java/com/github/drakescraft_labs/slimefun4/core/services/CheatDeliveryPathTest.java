package io.github.thebusybiscuit.slimefun4.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CheatDeliveryPathTest {

    @Test
    void categoryAndSearchUseTheSameDeliveryGate() throws IOException {
        String guide = Files.readString(Path.of(
                "src", "main", "java", "io", "github", "thebusybiscuit",
                "implementation", "guide", "SurvivalSlimefunGuide.java"));

        assertEquals(2, occurrences(guide, "CheatPolicy.claim("));
        assertFalse(guide.contains("p.getInventory().addItem(sfItem.getItem()"));
    }

    @Test
    void productionDefaultsKeepThePublishedRollingLimit() throws IOException {
        String config = Files.readString(Path.of("src", "main", "resources", "config.yml"));

        assertTrue(config.contains("max-claims: 32"));
        assertTrue(config.contains("premium-max-claims: 48"));
        assertTrue(config.contains("window-minutes: 60"));
        assertTrue(config.contains("limited-permission: \"odysseia.sfmaster.active\""));
        assertTrue(config.contains("premium-limit-permission: \"odysseia.sfmaster.titan\""));
    }

    @Test
    void limitedDeliveryUsesOneLegalStackPerClaim() throws IOException {
        String policy = Files.readString(Path.of(
                "src", "main", "java", "io", "github", "thebusybiscuit",
                "core", "services", "CheatPolicy.java"));

        assertTrue(policy.contains("limited ? claimed.getMaxStackSize()"));
        assertFalse(policy.contains("limited ? 1 :"));
    }

    @Test
    void laboratoryCanOpenCheatCatalogWithoutAdministrativeCommands() throws IOException {
        Path commands = Path.of(
                "src", "main", "java", "io", "github", "thebusybiscuit",
                "core", "commands", "subcommands");

        String cheat = Files.readString(commands.resolve("CheatCommand.java"));
        String give = Files.readString(commands.resolve("GiveCommand.java"));
        String research = Files.readString(commands.resolve("ResearchCommand.java"));
        String config = Files.readString(Path.of("src", "main", "resources", "config.yml"));

        assertTrue(cheat.contains("CheatPolicy.canUseCheat(player)"));
        assertTrue(give.contains("CheatPolicy.hasAdministrativeBypass(player)"));
        assertTrue(research.contains("CheatPolicy.hasAdministrativeBypass(player)"));
        assertTrue(config.contains("allowed-worlds:"));
        assertTrue(config.contains("- \"laboratorio\""));
    }

    @Test
    void laboratoryBookOpensDirectlyWithoutRunningTheCheatCommand() throws IOException {
        String listener = Files.readString(Path.of(
                "src", "main", "java", "io", "github", "thebusybiscuit",
                "implementation", "listeners", "SlimefunGuideListener.java"));
        String policy = Files.readString(Path.of(
                "src", "main", "java", "io", "github", "thebusybiscuit",
                "core", "services", "CheatPolicy.java"));

        assertTrue(listener.contains("PlayerChangedWorldEvent"));
        assertTrue(listener.contains("openGuide(p, e, SlimefunGuideMode.CHEAT_MODE)"));
        assertFalse(listener.contains("p.chat(\"/sf cheat\")"));
        assertTrue(policy.contains("hasAdministrativeBypass(player) || isLaboratoryAccess(player)"));
        assertTrue(policy.contains("if (limited || laboratory)"));
    }

    @Test
    void laboratoryResearchBypassIsVirtualAndNotPersisted() throws IOException {
        String profile = Files.readString(Path.of(
                "src", "main", "java", "io", "github", "thebusybiscuit",
                "api", "player", "PlayerProfile.java"));

        assertTrue(profile.contains("CheatPolicy.isLaboratoryAccess(owner)"));
        assertFalse(profile.contains("setResearched(research, CheatPolicy.isLaboratoryAccess"));
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
