package org.jackhuang.hmcl.modpack;

import org.jackhuang.hmcl.addon.AddonProjectInfo;
import org.jetbrains.annotations.NotNullByDefault;

@NotNullByDefault
public final class ModpackProjectInfo extends AddonProjectInfo {
    public ModpackProjectInfo(
            int mcmodId, String curseforgeId, String modrinthId,
            String mainName, String subName, String abbrName
    ) {
        super(mcmodId, curseforgeId, modrinthId, mainName, subName, abbrName);
    }

    @Override
    protected String mcmodClass() {
        return "modpack";
    }

    @Override
    protected String curseforgeClass() {
        return "modpacks";
    }

    @Override
    protected String modrinthClass() {
        return "modpack";
    }
}
