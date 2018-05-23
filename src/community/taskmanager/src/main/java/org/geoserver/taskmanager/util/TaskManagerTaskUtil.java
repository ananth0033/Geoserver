/* (c) 2017 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.taskmanager.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.geoserver.taskmanager.data.Attribute;
import org.geoserver.taskmanager.data.BatchRun;
import org.geoserver.taskmanager.data.Configuration;
import org.geoserver.taskmanager.data.Parameter;
import org.geoserver.taskmanager.data.Task;
import org.geoserver.taskmanager.data.TaskManagerFactory;
import org.geoserver.taskmanager.schedule.BatchContext;
import org.geoserver.taskmanager.schedule.ParameterInfo;
import org.geoserver.taskmanager.schedule.ParameterType;
import org.geoserver.taskmanager.schedule.TaskContext;
import org.geoserver.taskmanager.schedule.TaskException;
import org.geoserver.taskmanager.schedule.TaskType;
import org.geoserver.taskmanager.util.ValidationError.ValidationErrorType;
import org.geoserver.taskmanager.web.action.Action;
import org.geotools.util.logging.Logging;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Lookup;
import org.springframework.stereotype.Service;

/**
 *  Implementation independent helper methods.
 *  
 * @author Niels Charlier
 *
 */
@Service
public class TaskManagerTaskUtil {

    private static final Logger LOGGER = Logging.getLogger(TaskManagerTaskUtil.class);
    
    @Autowired
    private LookupService<TaskType> taskTypes;

    @Autowired
    private LookupService<Action> actions;
    
    @Autowired
    private TaskManagerFactory fac;

    @Autowired
    private TaskManagerDataUtil dataUtil;
    
    @Lookup
    public TaskContext createContext(Task task) {
        return null;
    }
    
    @Lookup
    public TaskContext createContext(Task task, BatchContext bc) {
        return null;
    }
    
    @Lookup
    public BatchContext createContext(BatchRun br) {
        return null;
    }
            
    private String getRawParameterValue(Parameter parameter) {
        String attName = dataUtil.getAssociatedAttributeName(parameter);
        if (attName != null) {
            Attribute att = parameter.getTask().getConfiguration().getAttributes().get(attName);
            if (att != null) {
                return att.getValue();
            } else {
                return null;
            }
        } else {
            return parameter.getValue();
        }
    }
    
    /**
     * Compile raw parameters from task using the configuration.
     * 
     * @param task the task
     * @return the raw parameters.
     */
    private Map<String, String> getRawParameterValues(Task task) {
        Map<String, String> rawParameterValues = new HashMap<String, String>();
        for (Parameter parameter : task.getParameters().values()) {
            String rawValue = getRawParameterValue(parameter);
            if (rawValue != null) {
                rawParameterValues.put(parameter.getName(), rawValue);
            }
        }
        return rawParameterValues;
    }

    /**
     * Validate required parameters
     * 
     * @param taskType task type
     * @param rawParameters raw parameters
     * @throws TaskException
     */
    private void validateRequired(TaskType taskType, Map<String, String> rawParameters, List<ValidationError> validationErrors)  {
        for (ParameterInfo info : taskType.getParameterInfo().values()) {
            if (info.isRequired()) {
                if (!rawParameters.containsKey(info.getName()) ||
                        "".equals(rawParameters.get(info.getName()))) {
                    validationErrors.add(new ValidationError(ValidationErrorType.MISSING, 
                            info.getName(), null, taskType.getName()));
                }
            }
        }
    }
    
    /**
     * validate and parse the raw parameters.
     * 
     * @param taskTypeName the task type name.
     * @param taskType the task type.
     * @param rawParameters the raw parameters.
     * @return the parsed parameters
     * @throws TaskException if any of the parameters are invalid or missing.
     */
    private Map<String, Object> parseParameters(TaskType taskType, 
            Map<String, String> rawParameters) throws TaskException {
        List<ValidationError> validationErrors = new ArrayList<ValidationError>();
        
        //first check all required
        validateRequired(taskType, rawParameters, validationErrors);
        
        //parse
        Map<String, Object> result = new HashMap<String, Object>();        
        for (Entry<String, String> parameter : rawParameters.entrySet()) {
            ParameterInfo info = taskType.getParameterInfo().get(parameter.getKey());
            if (info == null) {
                validationErrors.add(new ValidationError(ValidationErrorType.INVALID_PARAM, 
                        parameter.getKey(), null, taskType.getName()));
                break;
            }
            ParameterType pt = info.getType();
            List<String> dependsOnValues = new ArrayList<String>();
            for (ParameterInfo dependsOn : info.getDependsOn()) {
                dependsOnValues.add(rawParameters.get(dependsOn.getName()));
            }
            Object value = pt.parse(parameter.getValue(), dependsOnValues);
            if (value == null) {
                validationErrors.add(new ValidationError(ValidationErrorType.INVALID_VALUE, 
                        parameter.getKey(), parameter.getValue(), taskType.getName()));
                break;
            } else {
                result.put(parameter.getKey(), value);
            }
        }
        
        if (!validationErrors.isEmpty()) {
            throw new TaskException("There were validation errors: " + validationErrors);
        }
        
        return result;
    }
    
    /**
     * Clean-up a task.
     * 
     * @param task the task.
     * @return true if the cleanup was entirely successful, false if one or more task clean-ups failed,
     * in which case the logs should be checked.
     */
    public boolean cleanup(Task task) {
        TaskType type = taskTypes.get(task.getType());
        try {
            type.cleanup(createContext(task));
            return true;
        } catch (TaskException e) {
            LOGGER.log(Level.SEVERE, "Clean-up of task " + task.getFullName() + " failed", e);
            return false;
        }
    }
    
    /**
     * Can this taks be cleaned up?
     * @param task the task
     * 
     * @return whether the task can be cleaned-up
     */
    public boolean canCleanup(Task task) {
        return taskTypes.get(task.getType()).supportsCleanup();
    }
    
    /**
     * Clean-up a task.
     * 
     * @param task the task.
     * @return true if the cleanup was entirely successful, false if one or more task clean-ups failed,
     * in which case the logs should be checked.
     * @throws TaskException 
     */
    public Map<String, Object> getParameterValues(Task task) throws TaskException {
        return parseParameters(taskTypes.get(task.getType()), 
                getRawParameterValues(task));
    }
    
    /**
     * Clean-up a configuration.
     * 
     * @param config the configuration.
     * @return true if the cleanup was entirely successful, false if one or more task clean-ups failed,
     * in which case the logs should be checked.
     */
    public boolean cleanup(Configuration config) {
        boolean success = true;
        for (Task task : config.getTasks().values()) {
            if (canCleanup(task)) {
                success = success && cleanup(task);
            }
        }
        return success;        
    }
    
    /**
     * Can this configuration be cleaned up?
     * @param config the configuration
     * 
     * @return whether the configuration can be cleaned-up
     */
    public boolean canCleanup(Configuration config) {
        for (Task task : config.getTasks().values()) {
            if (canCleanup(task)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Creates and initializes a task of a particular type
     * 
     * @param type the type
     * @param name the name of the new task 
     * @return the task
     */
    public Task initTask(String type, String name) {
        Task task = fac.createTask();
        task.setType(type);
        task.setName(name);
        TaskType taskType = taskTypes.get(type);
        for (ParameterInfo info : taskType.getParameterInfo().values()) {
            Parameter param = fac.createParameter();
            param.setName(info.getName());
            param.setValue("${" + info.getName() + "}");
            param.setTask(task);
            task.getParameters().put(info.getName(), param);
        }
        return task;
    }
        
    /**
     * Duplicates a task to a new task
     * 
     * @param original the original task the type
     * @param name the name of the new task 
     * @return the task
     */
    public Task copyTask(Task original, String name) {
        Task task = fac.createTask();
        task.setType(original.getType());
        task.setName(name);
        for (Parameter originalParam : original.getParameters().values()) {
            Parameter param = fac.createParameter();
            param.setName(originalParam.getName());
            param.setValue(originalParam.getValue());
            param.setTask(task);
            task.getParameters().put(param.getName(), param);
        }
        return task;
    }
    
    private static class AttributeInfo {
        private ParameterType type;
        private List<String> dependsOn;
        
        public AttributeInfo(ParameterType type, List<String> dependsOn) {
            this.type = type;
            this.dependsOn = dependsOn;
        }
        
        @Override
        public boolean equals(Object o) {
            if (o instanceof AttributeInfo) {
                return type.equals(((AttributeInfo) o).type)
                        && dependsOn.equals(((AttributeInfo) o).dependsOn);
            }
            return false;
        }

        public ParameterType getType() {
            return type;
        }

        public List<String> getDependsOn() {
            return dependsOn;
        }
    }
    
    /**
     * Get attribute domain based on associated parameters.
     * 
     * @param type the type
     * @param string 
     * @return the task
     */
    private List<String> mergeDomain(Attribute attribute, Configuration config) {
        List<Parameter> params = dataUtil.getAssociatedParameters(attribute, config);
        
        Set<AttributeInfo> attInfos = new HashSet<AttributeInfo>();
        for (Parameter param : params) {
            TaskType taskType = taskTypes.get(param.getTask().getType());
            ParameterInfo info = taskType.getParameterInfo().get(param.getName());
            List<String> dependsOn = new ArrayList<String>();
            for (ParameterInfo dependsOnInfo : info.getDependsOn()) {
                Parameter dependsOnParam = param.getTask().getParameters().get(dependsOnInfo.getName());
                dependsOn.add(dependsOnParam == null ? null : getRawParameterValue(dependsOnParam));
            }
            attInfos.add(new AttributeInfo(info.getType(), dependsOn));
        }
        
        Set<String> domain = null;
        //first add all inclusive domains
        for (AttributeInfo attInfo : attInfos) {
            List<String> thisDomain = attInfo.getType().getDomain(attInfo.getDependsOn());            
            if (thisDomain != null && thisDomain.contains("")) {
                if (domain == null) {
                    domain = new LinkedHashSet<String>(thisDomain);
                } else {
                    domain.addAll(thisDomain);
                }
            }
        }
        //then select on all the exclusive domains
        for (AttributeInfo attInfo : attInfos) {
            List<String> thisDomain = attInfo.getType().getDomain(attInfo.getDependsOn());            
            if (thisDomain != null && !thisDomain.contains("")) {
                if (domain == null) {
                    domain = new LinkedHashSet<String>(thisDomain);
                } else {
                    domain.retainAll(thisDomain);
                }
            }
        }
        return domain == null ? null : new ArrayList<String>(domain);
    }
    
    /**
     * Get domains of configuration
     * 
     * @param configuration the configuration
     * @return the domains
     */
    public Map<String, List<String>> getDomains(Configuration configuration) {
        Map<String, List<String>> domains = new HashMap<String, List<String>>();
        for (Attribute att : configuration.getAttributes().values()) {
            domains.put(att.getName(), mergeDomain(att, configuration));
        }
        return domains;
    }

    public Set<ParameterType> getTypesForAttribute(Attribute attribute, Configuration config) {
        HashSet<ParameterType> parameterTypes = new HashSet<>();

        for (Parameter parameter : dataUtil.getAssociatedParameters(attribute, config)) {
            TaskType taskType = taskTypes.get(parameter.getTask().getType());
            parameterTypes.add(taskType.getParameterInfo().get(parameter.getName()).getType());
        }

        return parameterTypes;
    }
    
    /**
     * Update domains of configuration
     * 
     * @param configuration the configuration
     * @param the domains
     */
    public void updateDomains(Configuration configuration, Map<String, List<String>> domains,
            Set<String> updateAttributes) {
        Iterator<String> it = domains.keySet().iterator();
        while(it.hasNext()) {
            String attName = it.next();
            if (!configuration.getAttributes().containsKey(attName)) {
                it.remove();
            } 
        }
        for (Attribute att : configuration.getAttributes().values()) {
            if (!domains.containsKey(att.getName()) ||
                    updateAttributes != null && updateAttributes.contains(att.getName())) {
                domains.put(att.getName(), mergeDomain(att, configuration));
            }
        }
    }
    
    /**
     * Update dependent domains for a particular attribute.
     * 
     * @param attribute the attribute.
     * @param domains the domains.
     */
    public void updateDependentDomains(Attribute attribute, Configuration config,
            Map<String, List<String>> domains) {
        Set<String> dependentAttributes = new HashSet<String>();
        List<Parameter> params = dataUtil.getAssociatedParameters(attribute, config);
        for (Parameter param : params) {
            TaskType taskType = taskTypes.get(param.getTask().getType());
            ParameterInfo info = taskType.getParameterInfo().get(param.getName());
            for (ParameterInfo dependentInfo: info.getDependents()) {
                Parameter depedentParam = param.getTask().getParameters().get(dependentInfo.getName());
                if (depedentParam != null) {
                    String attName = dataUtil.getAssociatedAttributeName(depedentParam);
                    if (attName != null) {
                        dependentAttributes.add(attName);
                    }
                }
            }
        }
        
        for (String attName : dependentAttributes) {
            Attribute att = attribute.getConfiguration().getAttributes().get(attName);
            if (att != null) {
                domains.put(attName, mergeDomain(att, config));
            }
        }
    }

    /**
     * Validate configuration (at configuration time)
     * 
     * @param configuration the configuration
     * @throws TaskException
     */
    public List<ValidationError> validate(Configuration configuration) {
        List<ValidationError> validationErrors = new ArrayList<ValidationError>();
        
        for (Task task : configuration.getTasks().values()) {
            TaskType taskType = taskTypes.get(task.getType());
            Map<String, String> rawParameters = getRawParameterValues(task);
            //first check all required (except if template)
            if (!configuration.isTemplate()) {
                validateRequired(taskType, rawParameters, validationErrors);
            }
            
            for (Entry<String, String> parameter : rawParameters.entrySet()) {
                ParameterInfo info = taskType.getParameterInfo().get(parameter.getKey());
                if (info == null) {
                    validationErrors.add(new ValidationError(ValidationErrorType.INVALID_PARAM, 
                            parameter.getKey(), null, taskType.getName()));
                    continue;
                }
                ParameterType pt = info.getType();
                List<String> dependsOnValues = new ArrayList<String>();
                for (ParameterInfo dependsOn : info.getDependsOn()) {
                    dependsOnValues.add(rawParameters.get(dependsOn.getName()));
                }
                if (!pt.validate(parameter.getValue(), dependsOnValues)) {
                    validationErrors.add(new ValidationError(ValidationErrorType.INVALID_VALUE, 
                            parameter.getKey(), parameter.getValue(), taskType.getName()));
                    continue;
                }
            }
            
        }
        
        return validationErrors;
    }
    
    /**
     * Get attribute domain based on associated parameters.
     * 
     * @param type the type
     * @param string 
     * @return the task
     */
    public List<Action> getActionsForAttribute(Attribute attribute, Configuration config) {
        List<Parameter> params = dataUtil.getAssociatedParameters(attribute, config);
        
        Set<Action> result = new HashSet<Action>();
        for (Parameter param : params) {
            TaskType taskType = taskTypes.get(param.getTask().getType());
            ParameterInfo info = taskType.getParameterInfo().get(param.getName());
            for (String actionName : info.getType().getActions()) {
                result.add(actions.get(actionName));
            }
        }
        
        return new ArrayList<Action>(result);
    }
    

}
