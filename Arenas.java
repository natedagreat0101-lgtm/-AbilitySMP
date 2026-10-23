package me.abilitysmp;

import org.bukkit.Material;
import org.bukkit.World;

/** Builds the round arenas used by duels and the Headless Horseman fight. */
final class Arenas {

    private Arenas() {
    }

    static boolean built(World w, int cx, int y, int cz) {
        return w.getBlockAt(cx, y, cz).getType() != Material.AIR;
    }

    /** A round platform with a darker rim, clear air above it and invisible barrier walls and ceiling. */
    static void build(World w, int cx, int y, int cz, int r, Material floor, Material rim, int wallHeight) {
        for (int dx = -r - 2; dx <= r + 2; dx++) {
            for (int dz = -r - 2; dz <= r + 2; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 <= r * r) {
                    boolean edge = d2 > (r - 2) * (r - 2);
                    w.getBlockAt(cx + dx, y, cz + dz).setType(edge ? rim : floor, false);
                    w.getBlockAt(cx + dx, y - 1, cz + dz).setType(floor, false);
                    for (int h = 1; h <= wallHeight + 1; h++) w.getBlockAt(cx + dx, y + h, cz + dz).setType(Material.AIR, false);
                    w.getBlockAt(cx + dx, y + wallHeight + 2, cz + dz).setType(Material.BARRIER, false);
                } else if (d2 <= (r + 2) * (r + 2)) {
                    for (int h = 1; h <= wallHeight + 2; h++) w.getBlockAt(cx + dx, y + h, cz + dz).setType(Material.BARRIER, false);
                }
            }
        }
    }
}
