package tools.data;

/**
 * The swap seam for where tool Q-action blueprints come from. Today it's a JSON file
 * ({@link JsonToolActionDataSource}) with a built-in fallback ({@link DefaultToolActionDataSource});
 * a hand-rolled loader later is a one-class change. Deliberately free of engine types.
 */
public interface ToolActionDataSource {
    ToolActionBlueprint[] loadToolActionBlueprints();
}
