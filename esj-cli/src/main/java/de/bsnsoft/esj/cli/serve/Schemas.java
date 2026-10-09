package de.bsnsoft.esj.cli.serve;

import java.util.ArrayList;
import java.util.List;

/**
 * The JSON Schema of the arguments of a tool, for {@code tools/list} and the OpenAPI
 * description alike.
 */
final class Schemas {

    private Schemas() {
    }

    /**
     * Returns the schema of the arguments of a tool.
     *
     * @param tool      the tool
     * @param frontDoor the parameters the front door adds
     * @return an object schema that names every parameter and admits no other
     */
    static Jv.Obj input(Tools.Tool tool, List<Tools.Param> frontDoor) {
        Jv.Builder properties = Jv.object();
        List<Jv> required = new ArrayList<>();
        List<Tools.Param> all = new ArrayList<>(frontDoor);
        all.addAll(tool.params());
        for (Tools.Param param : all) {
            properties.put(param.name(), property(param));
            if (param.required()) {
                required.add(Jv.of(param.name()));
            }
        }
        Jv.Builder schema = Jv.object()
                .put("type", "object")
                .put("properties", properties.build());
        if (!required.isEmpty()) {
            schema.put("required", Jv.array(required));
        }
        return schema.put("additionalProperties", false).build();
    }

    /**
     * Returns the schema of one parameter.
     *
     * @param param the parameter
     * @return its schema, with its description, its choices and its default
     */
    static Jv.Obj property(Tools.Param param) {
        Jv.Builder property = Jv.object().put("type", param.type().schema());
        if (param.type() == Tools.Type.STRINGS) {
            Jv.Builder items = Jv.object().put("type", "string");
            if (!param.choices().isEmpty()) {
                items.put("enum", choices(param));
            }
            property.put("items", items.build());
            if ("paths".equals(param.name())) {
                property.put("minItems", 1).put("maxItems", Tools.MAX_PATHS);
            }
        } else if (!param.choices().isEmpty()) {
            property.put("enum", choices(param));
        }
        param.fallback().ifPresent(fallback -> property.put("default",
                param.type() == Tools.Type.BOOLEAN ? Jv.of(Boolean.parseBoolean(fallback))
                        : Jv.of(fallback)));
        return property.put("description", param.description()).build();
    }

    private static Jv choices(Tools.Param param) {
        List<Jv> values = new ArrayList<>();
        param.choices().forEach(choice -> values.add(Jv.of(choice)));
        return Jv.array(values);
    }
}
