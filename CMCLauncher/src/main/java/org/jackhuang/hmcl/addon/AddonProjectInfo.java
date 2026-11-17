package org.jackhuang.hmcl.addon;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

@NotNullByDefault
public abstract class AddonProjectInfo {

    @Range(from = 0, to = Integer.MAX_VALUE)
    private final int mcmodId;
    private final String curseforgeId;
    private final String modrinthId;

    private final String mainName;
    private final String subName;
    private final String abbrName;

    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private @Nullable Object keepAlive;

    protected AddonProjectInfo(
            int mcmodId, String curseforgeId, String modrinthId,
            String mainName, String subName, String abbrName) {
        if (mainName.isEmpty()) {
            throw new IllegalArgumentException("main name must not be empty");
        }

        this.mcmodId = mcmodId;
        this.curseforgeId = curseforgeId;
        this.modrinthId = modrinthId;
        this.mainName = mainName;
        this.subName = subName;
        this.abbrName = abbrName;
    }

    public String curseforgeId() {
        return curseforgeId;
    }

    public String mainName() {
        return mainName;
    }

    public String subName() {
        return subName;
    }

    public String abbrName() {
        return abbrName;
    }

    public String displayName() {
        StringBuilder builder = new StringBuilder();
        if (!abbrName.isBlank())
            builder.append('[').append(abbrName.trim()).append("] ");
        builder.append(mainName);
        if (!subName.isBlank())
            builder.append(" (").append(subName).append(')');
        return builder.toString();
    }

    public void setKeepAlive(@Nullable Object keepAlive) {
        this.keepAlive = keepAlive;
    }

    protected abstract String mcmodClass();

    protected abstract String curseforgeClass();

    protected abstract String modrinthClass();

    public @Nullable String mcmodUrl() {
        return mcmodId > 0
                ? "https://www.mcmod.cn/" + mcmodClass() + "/" + mcmodId + ".html"
                : null;
    }

    public @Nullable String curseforgeUrl() {
        return !curseforgeId.isEmpty()
                ? "https://www.curseforge.com/minecraft/" + curseforgeClass() + "/" + curseforgeId
                : null;
    }

    public @Nullable String modrinthUrl() {
        return !modrinthId.isEmpty()
                ? "https://modrinth.com/" + modrinthClass() + "/" + modrinthId
                : null;
    }

}
