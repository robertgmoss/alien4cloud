package alien4cloud.utils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.MapUtils;

import alien4cloud.exception.InvalidArgumentException;
import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.components.ComplexPropertyValue;
import alien4cloud.model.components.ListPropertyValue;
import alien4cloud.model.components.PropertyDefinition;
import alien4cloud.model.components.ScalarPropertyValue;
import alien4cloud.model.topology.Capability;
import alien4cloud.model.topology.NodeTemplate;

import com.google.common.collect.Maps;

public final class PropertyUtil {
    private PropertyUtil() {
    }

    /**
     * Convert a map of property definitions to a map of property values based on the default values specified.
     * <p/>
     * Note: This method will have to be removed once the ui manages properties correctly.
     *
     * @param propertyDefinitions The map of {@link PropertyDefinition}s to convert.
     * @return An equivalent map of default {@link alien4cloud.model.components.ScalarPropertyValue}s, that contains all properties definitions keys (default
     *         value
     *         is null when no default value is specified in the property definition).
     */
    public static Map<String, AbstractPropertyValue> getDefaultPropertyValuesFromPropertyDefinitions(Map<String, PropertyDefinition> propertyDefinitions) {
        if (propertyDefinitions == null) {
            return null;
        }

        Map<String, AbstractPropertyValue> defaultPropertyValues = Maps.newHashMap();

        for (Map.Entry<String, PropertyDefinition> entry : propertyDefinitions.entrySet()) {
            String defaultValue = entry.getValue().getDefault();
            if (defaultValue != null && !defaultValue.trim().isEmpty()) {
                defaultPropertyValues.put(entry.getKey(), new ScalarPropertyValue(defaultValue));
            } else {
                defaultPropertyValues.put(entry.getKey(), null);
            }
        }

        return defaultPropertyValues;
    }

    public static String getDefaultValueFromPropertyDefinitions(String propertyName, Map<String, PropertyDefinition> propertyDefinitions) {
        if (MapUtils.isNotEmpty(propertyDefinitions) && propertyDefinitions.containsKey(propertyName)) {
            String defaultValue = propertyDefinitions.get(propertyName).getDefault();
            return defaultValue;
        } else {
            return null;
        }
    }

    /**
     * Get the property from a complex path. If the path is simple, this method will return null.
     * A complex path is containing '.'
     *
     * @param propertyPath the complex property path
     * @return the first element of the path (property name)
     */
    public static String getPropertyNameFromComplexPath(String propertyPath) {
        if (propertyPath.contains(".")) {
            String[] paths = propertyPath.split("\\.");
            return paths[0];
        } else {
            return null;
        }
    }

    public static void setPropertyValue(Map<String, AbstractPropertyValue> properties, PropertyDefinition propertyDefinition, String propertyName,
            Object propertyValue) {
        // take the default value
        if (propertyValue == null) {
            propertyValue = propertyDefinition.getDefault();
        }

        // if the default value is also empty, we set the property value to null
        if (propertyValue == null) {
            properties.put(propertyName, null);
        } else {
            if (propertyValue instanceof String) {
                properties.put(propertyName, new ScalarPropertyValue((String) propertyValue));
            } else if (propertyValue instanceof Map) {
                properties.put(propertyName, new ComplexPropertyValue((Map<String, Object>) propertyValue));
            } else if (propertyValue instanceof List) {
                properties.put(propertyName, new ListPropertyValue((List<Object>) propertyValue));
            } else {
                throw new InvalidArgumentException("Property type " + propertyValue.getClass().getName() + " is invalid");
            }
        }
    }

    /**
     * Set value for a property
     *
     * @param nodeTemplate the node template
     * @param propertyDefinition the definition of the property to be set
     * @param propertyName the name of the property to set
     * @param propertyValue the value to be set
     */
    public static void setPropertyValue(NodeTemplate nodeTemplate, PropertyDefinition propertyDefinition, String propertyName, Object propertyValue) {
        if (nodeTemplate.getProperties() == null) {
            nodeTemplate.setProperties(Maps.<String, AbstractPropertyValue> newHashMap());
        }
        setPropertyValue(nodeTemplate.getProperties(), propertyDefinition, propertyName, propertyValue);
    }

    /**
     * Set value for a capability property
     *
     * @param capability the capability
     * @param propertyDefinition the definition of the property
     * @param propertyName the name of the property
     * @param propertyValue the value of the property
     */
    public static void setCapabilityPropertyValue(Capability capability, PropertyDefinition propertyDefinition, String propertyName, Object propertyValue) {
        if (capability.getProperties() == null) {
            capability.setProperties(Maps.<String, AbstractPropertyValue> newHashMap());
        }
        setPropertyValue(capability.getProperties(), propertyDefinition, propertyName, propertyValue);
    }

    /**
     * Merge from map into 'into' map
     *
     * @param from from map
     * @param into into map
     * @param keysToConsider if defined only keys contained by this set are considered
     */
    public static void mergeProperties(Map<String, AbstractPropertyValue> from, Map<String, AbstractPropertyValue> into, Set<String> keysToConsider) {
        if (MapUtils.isNotEmpty(from)) {
            for (Map.Entry<String, AbstractPropertyValue> fromEntry : from.entrySet()) {
                if (keysToConsider != null && !keysToConsider.contains(fromEntry.getKey())) {
                    // If the key filter do not contain the key then do not consider
                    continue;
                }
                AbstractPropertyValue existingValue = into.get(fromEntry.getKey());
                if (fromEntry.getValue() != null || existingValue == null) {
                    into.put(fromEntry.getKey(), fromEntry.getValue());
                }
            }
        }
    }
}