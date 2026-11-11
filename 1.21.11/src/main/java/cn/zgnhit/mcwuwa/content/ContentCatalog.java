package cn.zgnhit.mcwuwa.content;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.List;
import java.util.ArrayList;

/** Loads fixed classpath resources, never datapacks, web pages or client-supplied values. */
public final class ContentCatalog {
    private static final String PREFIX = "/mc_wuwa/combat/";
    private static final Gson GSON = new Gson();
    private final Map<String, CharacterDefinition> byWeapon;
    private final Map<Integer, CharacterDefinition> bySource;
    private final Map<String, CharacterDefinition> byCharacter;
    private final JsonObject manifest;
    private final Map<Integer, JsonObject> sonatas;
    private final Map<String, List<ActionDamageMapping>> actions;
    private final Map<Integer, JsonObject> echoes;

    private ContentCatalog(Map<String, CharacterDefinition> weapons, JsonObject manifest) {
        this.byWeapon = Map.copyOf(weapons);
        Map<Integer, CharacterDefinition> sources = new LinkedHashMap<>();
        Map<String, CharacterDefinition> characters = new LinkedHashMap<>();
        for (CharacterDefinition definition : weapons.values()) {
            if (sources.put(definition.sourceId(), definition) != null
                    || characters.put(definition.characterId(), definition) != null) {
                throw new IllegalStateException("Duplicate frozen character: " + definition.id());
            }
        }
        this.bySource = Map.copyOf(sources);
        this.byCharacter = Map.copyOf(characters);
        this.manifest = manifest.deepCopy();
        Map<Integer, JsonObject> loadedSonatas = new LinkedHashMap<>();
        for (var value : readJson("sonatas.json").getAsJsonArray()) {
            var sonata = value.getAsJsonObject();
            loadedSonatas.put(sonata.get("sourceId").getAsInt(), sonata.deepCopy());
        }
        this.sonatas = Map.copyOf(loadedSonatas);
        Map<String, List<ActionDamageMapping>> loadedActions = new LinkedHashMap<>();
        for (String id : characters.keySet()) {
            List<ActionDamageMapping> mappings = new ArrayList<>();
            for (var value : readObject("actions/" + id + ".json").getAsJsonArray("actions")) mappings.add(GSON.fromJson(value, ActionDamageMapping.class));
            loadedActions.put(id, List.copyOf(mappings));
        }
        this.actions = Map.copyOf(loadedActions);
        Map<Integer, JsonObject> loadedEchoes = new LinkedHashMap<>();
        for (var value : manifest.getAsJsonArray("echoes")) {
            JsonObject entry = value.getAsJsonObject();
            loadedEchoes.put(entry.get("sourceId").getAsInt(), readObject(entry.get("resource").getAsString()));
        }
        this.echoes = Map.copyOf(loadedEchoes);
    }

    public static ContentCatalog loadDefault() {
        JsonObject manifest = readObject("catalog.json");
        if (manifest.get("schemaVersion").getAsInt() != 1
                || !manifest.get("dataVersion").getAsString().equals("3.7")) {
            throw new IllegalStateException("Unsupported frozen combat data schema/version");
        }
        Map<String, CharacterDefinition> definitions = new LinkedHashMap<>();
        for (var entry : manifest.getAsJsonArray("characters")) {
            JsonObject index = entry.getAsJsonObject();
            CharacterDefinition definition = GSON.fromJson(readObject(index.get("resource").getAsString()), CharacterDefinition.class);
            if (!definition.id().equals(index.get("id").getAsString()) || definition.sonataSlots().size() != 5
                    || definition.chains().size() != 6 || definition.weapon().refinements().size() != 5
                    || definitions.put(definition.id(), definition) != null) {
                throw new IllegalStateException("Invalid frozen character: " + index);
            }
        }
        if (definitions.size() != cn.zgnhit.mcwuwa.core.CharacterId.values().length) throw new IllegalStateException("Frozen catalog must cover every registered character");
        return new ContentCatalog(definitions, manifest);
    }

    public CharacterDefinition requireByWeapon(String registryId) {
        String id = registryId.startsWith("mc_wuwa:") ? registryId.substring("mc_wuwa:".length()) : registryId;
        CharacterDefinition definition = byWeapon.get(id);
        if (definition == null) throw new NoSuchElementException("Unknown mc_wuwa weapon: " + registryId);
        return definition;
    }

    public CharacterDefinition requireByCharacter(String characterId) {
        CharacterDefinition definition = byCharacter.get(characterId);
        if (definition == null) throw new NoSuchElementException("Unknown mc_wuwa character: " + characterId);
        return definition;
    }

    public CharacterDefinition requireBySourceId(int sourceId) {
        CharacterDefinition definition = bySource.get(sourceId);
        if (definition == null) throw new NoSuchElementException("Unknown Nanoka character: " + sourceId);
        return definition;
    }

    public Collection<CharacterDefinition> characters() { return byWeapon.values(); }
    public JsonObject manifest() { return manifest.deepCopy(); }
    public JsonObject echo(int sourceId) {
        JsonObject echo = echoes.get(sourceId);
        if (echo == null) throw new NoSuchElementException("Unknown frozen echo: " + sourceId);
        return echo.deepCopy();
    }
    public JsonObject sonata(int sourceId) {
        JsonObject sonata = sonatas.get(sourceId);
        if (sonata == null) throw new NoSuchElementException("Unknown frozen sonata: " + sourceId);
        return sonata.deepCopy();
    }
    public String sonataName(int sourceId) { return sonata(sourceId).get("name").getAsString(); }
    /** Source groups; does not claim missing character rules have been implemented. */
    public List<ActionDamageMapping> actionMappings(String characterId) {
        requireByCharacter(characterId);
        return actions.get(characterId);
    }

    private static JsonObject readObject(String resource) {
        return readJson(resource).getAsJsonObject();
    }
    private static JsonElement readJson(String resource) {
        if (resource.contains("..") || resource.startsWith("/")) throw new IllegalArgumentException("Invalid resource path");
        try (InputStream stream = ContentCatalog.class.getResourceAsStream(PREFIX + resource)) {
            if (stream == null) throw new IllegalStateException("Missing frozen combat resource: " + resource);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read frozen combat resource: " + resource, exception);
        }
    }
}
