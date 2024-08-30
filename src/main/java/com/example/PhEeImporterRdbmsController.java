package com.example;
 
// Kubernetes API model imports
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.*;
import io.fabric8.kubernetes.api.model.rbac.*;
import io.fabric8.kubernetes.api.model.networking.v1.*;

// Kubernetes client imports
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;

// Operator SDK imports
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

// Logging imports
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Custom classes and utils
import com.example.customresource.PhEeImporterRdbms;
import com.example.utils.LoggingUtil;
import com.example.utils.StatusUpdateUtil;
import com.example.utils.ProbeUtils; 
import com.example.utils.ResourceDeletionUtil;
import com.example.customresource.PhEeImporterRdbmsSpec;


// java utils
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;


@ControllerConfiguration
public class PhEeImporterRdbmsController implements Reconciler<PhEeImporterRdbms> {

    private static final Logger log = LoggerFactory.getLogger(PhEeImporterRdbmsController.class);

    private final KubernetesClient kubernetesClient;

    public PhEeImporterRdbmsController(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
    }

    @Override
    public UpdateControl<PhEeImporterRdbms> reconcile(PhEeImporterRdbms resource, Context<PhEeImporterRdbms> context) {
        String resourceName = resource.getMetadata().getName();

        // Check if the deployment is disabled
        if (resource.getSpec().getEnabled() == null || !resource.getSpec().getEnabled()) {
            log.info("Deployment {} is disabled, deleting all associated resources.", resourceName);
            ResourceDeletionUtil.deleteResources(kubernetesClient, resource);
            return StatusUpdateUtil.updateDisabledStatus(kubernetesClient, resource);
        }

        // Deployment is enabled, proceed with individual resource checks
        LoggingUtil.logResourceDetails(resource);

        try {
            // Check and reconcile RBACs
            if (resource.getSpec().getRbacEnabled() == null || !resource.getSpec().getRbacEnabled()) {
                log.info("RBACs for resource {} are disabled, deleting associated RBAC resources.", resourceName);
                ResourceDeletionUtil.deleteRbacResources(kubernetesClient, resource);
            } else {
                reconcileServiceAccount(resource);
                reconcileClusterRole(resource);
                reconcileClusterRoleBinding(resource);
                reconcileRole(resource);
                reconcileRoleBinding(resource);
            }

            // Check and reconcile Secrets
            if (resource.getSpec().getSecretEnabled() == null || !resource.getSpec().getSecretEnabled()) {
                log.info("Secrets for resource {} are disabled, deleting associated Secret resources.", resourceName);
                ResourceDeletionUtil.deleteSecretResources(kubernetesClient, resource);
            } else {
                reconcileSecret(resource);
            } 

            // Check and reconcile ConfigMaps
            if (resource.getSpec().getConfigMapEnabled() == null || !resource.getSpec().getConfigMapEnabled()) {
                log.info("ConfigMap for resource {} is disabled, deleting associated ConfigMap resources.", resourceName);
                ResourceDeletionUtil.deleteConfigMapResources(kubernetesClient, resource);
            } else {
                reconcileConfigmap(resource);
            }

            // Check and reconcile Ingress
            if (resource.getSpec().getIngressEnabled() == null || !resource.getSpec().getIngressEnabled()) {
                log.info("Ingress for resource {} is disabled, deleting associated Ingress resources.", resourceName);
                ResourceDeletionUtil.deleteIngressResources(kubernetesClient, resource);
            } else {
                reconcileIngress(resource);
                reconcileServices(resource);
            }

            // Always reconcile the Deployment itself
            reconcileDeployment(resource);

            // Return success status update
            return StatusUpdateUtil.updateStatus(kubernetesClient, resource, resource.getSpec().getReplicas(), resource.getSpec().getImage(), true, "");

        } catch (Exception e) {
            // Log the error and return an error status update
            LoggingUtil.logError("Error during reconciliation for resource " + resourceName, e);
            return StatusUpdateUtil.updateErrorStatus(kubernetesClient, resource, resource.getSpec().getImage(), e);
        }
    } 
    
    private void reconcileDeployment(PhEeImporterRdbms resource) {
        log.info("Reconciling Deployment for resource: {}", resource.getMetadata().getName());
        Deployment deployment = createDeployment(resource);
        log.info("Created Deployment spec: {}", deployment);

        Resource<Deployment> deploymentResource = kubernetesClient.apps().deployments()
                .inNamespace(resource.getMetadata().getNamespace())
                .withName(resource.getMetadata().getName());

        if (deploymentResource.get() == null) {
            deploymentResource.create(deployment);
            log.info("Created new Deployment: {}", resource.getMetadata().getName());
        } else {
            deploymentResource.patch(deployment);
            log.info("Updated existing Deployment: {}", resource.getMetadata().getName());
        }
    }

    private Deployment createDeployment(PhEeImporterRdbms resource) {
        log.info("Creating Deployment spec for resource: {}", resource.getMetadata().getName());

        // Define labels for the Deployment and Pod templates
        Map<String, String> labels = new HashMap<>();
        labels.put("app", resource.getMetadata().getName());
        labels.put("managed-by", "ph-ee-importer-operator"); // Optional label for identifying managed resources


        // Build the container with environment variables, resources, and volume mounts
        ContainerBuilder containerBuilder = new ContainerBuilder()
            .withName(resource.getMetadata().getName())
            .withImage(resource.getSpec().getImage())
            .withEnv(createEnvironmentVariables(resource))
            .withResources(createResourceRequirements(resource))
            .withLivenessProbe(ProbeUtils.createProbe(resource, "liveness"))
            .withReadinessProbe(ProbeUtils.createProbe(resource, "readiness"))
            .withPorts(new ContainerPortBuilder()
                .withContainerPort(resource.getSpec().getContainerPort())
                .build());

        log.info("VolMount: {}", resource.getSpec().getVolMount());
        // Add volume mount conditionally
        if (resource.getSpec().getVolMount() != null && resource.getSpec().getVolMount().getEnabled()) {
            containerBuilder.withVolumeMounts(new VolumeMountBuilder()
                .withName(resource.getSpec().getVolMount().getName())
                .withMountPath("/config")
                .build());
        }


        Container container = containerBuilder.build();

        // Create PodSpec with the defined container and volumes
        PodSpecBuilder podSpecBuilder = new PodSpecBuilder()
            .withContainers(container);

        // Add volumes conditionally
        if (resource.getSpec().getVolMount() != null && resource.getSpec().getVolMount().getEnabled()) {
            podSpecBuilder.withVolumes(new VolumeBuilder()
                .withName(resource.getSpec().getVolMount().getName())
                .withConfigMap(new ConfigMapVolumeSourceBuilder()
                    .withName(resource.getSpec().getVolMount().getName())
                    .build())
                .build());
        }

        PodSpec podSpec = podSpecBuilder.build();

            // Build the PodTemplateSpec with metadata and spec
            PodTemplateSpec podTemplateSpec = new PodTemplateSpecBuilder()
                .withNewMetadata()
                    .withLabels(labels)
                .endMetadata()
                .withSpec(podSpec)
                .build();

            // Define the DeploymentSpec with replicas, selector, and template
            DeploymentSpec deploymentSpec = new DeploymentSpecBuilder()
                .withReplicas(resource.getSpec().getReplicas())
                .withSelector(new LabelSelectorBuilder()
                    .withMatchLabels(labels)
                    .build())
                .withTemplate(podTemplateSpec)
                .build();

        // // Get the current timestamp for the deployTime annotation
        // String deployTime = Instant.now().toString();

        // Create Deployment metadata with owner references
        ObjectMeta metadata = new ObjectMetaBuilder()
            .withName(resource.getMetadata().getName())
            .withNamespace(resource.getMetadata().getNamespace())
            .withLabels(labels)
            // .withAnnotations(Collections.singletonMap("example.com/deployTime", deployTime))
            .withOwnerReferences(createOwnerReferences(resource))
            .build();

        // Build the final Deployment object
        return new DeploymentBuilder()
            .withMetadata(metadata)
            .withSpec(deploymentSpec)
            .build();
    }

    private List<EnvVar> createEnvironmentVariables(PhEeImporterRdbms resource) {
        return resource.getSpec().getEnvironment().stream()
            .map(env -> {
                EnvVarBuilder envVarBuilder = new EnvVarBuilder().withName(env.getName());

                // Handle direct value
                if (env.getValue() != null) {
                    envVarBuilder.withValue(env.getValue());
                } 
                // Handle value from secret
                else if (env.getValueFrom() != null && env.getValueFrom().getSecretKeyRef() != null) {
                    envVarBuilder.withValueFrom(new EnvVarSourceBuilder()
                        .withSecretKeyRef(new SecretKeySelectorBuilder()
                            .withName(env.getValueFrom().getSecretKeyRef().getName())
                            .withKey(env.getValueFrom().getSecretKeyRef().getKey())
                            .build())
                        .build());
                } 
                // Optional: Add logging or error handling if needed
                else {
                    log.warn("Environment variable {} has no value or valueFrom defined.", env.getName());
                }

                return envVarBuilder.build();
            })
            .collect(Collectors.toList());
    }

    // Helper method to create resource requirements
    private ResourceRequirements createResourceRequirements(PhEeImporterRdbms resource) {
        return new ResourceRequirementsBuilder()
            .withLimits(new HashMap<String, Quantity>() {{
                put("cpu", new Quantity(resource.getSpec().getResources().getLimits().getCpu()));
                put("memory", new Quantity(resource.getSpec().getResources().getLimits().getMemory()));
            }})
            .withRequests(new HashMap<String, Quantity>() {{
                put("cpu", new Quantity(resource.getSpec().getResources().getRequests().getCpu()));
                put("memory", new Quantity(resource.getSpec().getResources().getRequests().getMemory()));
            }})
            .build();
    }
 
    // Reconcile Service
    private void reconcileServices(PhEeImporterRdbms resource) {
        log.info("Reconciling Services for resource: {}", resource.getMetadata().getName());

        // Create services
        List<Service> desiredServices = createServices(resource);
        log.info("Desired Service specs: {}", desiredServices.stream().map(Service::toString).collect(Collectors.joining(", ")));

        // Retrieve existing services
        List<Service> existingServices = kubernetesClient.services()
                .inNamespace(resource.getMetadata().getNamespace())
                .list()
                .getItems()
                .stream()
                .filter(service -> desiredServices.stream().anyMatch(desiredService -> desiredService.equals(service)))
                .collect(Collectors.toList());

        // Iterate over each desired service and reconcile
        for (Service desiredService : desiredServices) {
            Optional<Service> existingServiceOpt = existingServices.stream()
                    .filter(existingService -> existingService.equals(desiredService))
                    .findFirst();

            if (existingServiceOpt.isPresent()) {
                // Update existing Service if it matches the desired spec
                Service existingService = existingServiceOpt.get();
                kubernetesClient.services()
                    .inNamespace(resource.getMetadata().getNamespace())
                    .withName(existingService.getMetadata().getName())
                    .patch(desiredService);
                log.info("Updated existing Service: {}", desiredService.getMetadata().getName());
            } else {
                // Create new Service if it does not exist
                kubernetesClient.services()
                    .inNamespace(resource.getMetadata().getNamespace())
                    .create(desiredService);
                log.info("Created new Service: {}", desiredService.getMetadata().getName());
            }
        }
    }

    // Note: the services created will be using the names provided in the CR not in the reconcile method,
    // So it needs some work will be restructuring it later.
    // Create Services
    private List<Service> createServices(PhEeImporterRdbms resource) {
        log.info("Creating Services spec for resource: {}", resource.getMetadata().getName());

        PhEeImporterRdbmsSpec spec = resource.getSpec();
        List<PhEeImporterRdbmsSpec.Service> serviceSpecs = spec.getServices();

        return serviceSpecs.stream()
                .map(serviceSpec -> {
                    // Build ports
                    List<ServicePort> ports = serviceSpec.getPorts().stream()
                            .map(portSpec -> new ServicePortBuilder()
                                    .withName(portSpec.getName())
                                    .withPort(portSpec.getPort())
                                    .withTargetPort(new IntOrString(portSpec.getTargetPort()))
                                    .withProtocol(portSpec.getProtocol())
                                    .build())
                            .collect(Collectors.toList());

                    // Build Service
                    return new ServiceBuilder()
                            .withNewMetadata()
                                .withName(serviceSpec.getName())
                                .withNamespace(resource.getMetadata().getNamespace())
                                .withLabels(Map.of(
                                    "app", resource.getMetadata().getName(),
                                    "app.kubernetes.io/managed-by", "phee-importer-operator"
                                ))
                                .withAnnotations(serviceSpec.getAnnotations())
                                .withOwnerReferences(createOwnerReferences(resource))
                            .endMetadata()
                            .withNewSpec()
                                .withSelector(serviceSpec.getSelector() != null ? serviceSpec.getSelector() :
                                        Collections.singletonMap("app", resource.getMetadata().getName()))
                                .withPorts(ports)
                                .withType(serviceSpec.getType() != null ? serviceSpec.getType() : "ClusterIP")
                                .withSessionAffinity(serviceSpec.getSessionAffinity())
                            .endSpec()
                            .build();
                })
                .collect(Collectors.toList());
    }

    private void reconcileIngress(PhEeImporterRdbms resource) {
        String ingressName = resource.getMetadata().getName() + "-ingress";
        log.info("Reconciling Ingress for resource: {}", resource.getMetadata().getName());
        
        // Create Ingress
        Ingress ingress = createIngress(resource, ingressName);
        log.info("Created Ingress spec: {}", ingress);

        Resource<Ingress> ingressResource = kubernetesClient.network().v1().ingresses()
                .inNamespace(resource.getMetadata().getNamespace())
                .withName(ingressName);

        if (ingressResource.get() == null) {
            ingressResource.create(ingress);
            log.info("Created new Ingress: {}", ingressName);
        } else {
            ingressResource.patch(ingress);
            log.info("Updated existing Ingress: {}", ingressName);
        }
    }

    // This might needs changes, specially the spec and CRD for ingress 
    private Ingress createIngress(PhEeImporterRdbms resource, String ingressName) {
        log.info("Creating Ingress spec for resource: {}", resource.getMetadata().getName());

        // Extract values from the Custom Resource
        String host = resource.getSpec().getIngress().getHost(); 
        String path = resource.getSpec().getIngress().getPath(); 
        String serviceName = resource.getMetadata().getName() + "-svc";
        int servicePort = 8080; // Use the port defined in your values or configuration

        // Convert custom TLS objects to Fabric8's IngressTLS
        List<IngressTLS> ingressTlsList = resource.getSpec().getIngress().getTls().stream()
            .map(tls -> new IngressTLS(tls.getHosts(), tls.getSecretName()))
            .collect(Collectors.toList());

        List<IngressRule> rules = resource.getSpec().getIngress().getRules().stream()
            .map(rule -> new IngressRuleBuilder() // Map your custom rule to IngressRule
                .withHost(rule.getHost())
                .withNewHttp()
                    .addAllToPaths(rule.getPaths().stream().map(customPath -> 
                        new HTTPIngressPathBuilder() // Map your custom path to HTTPIngressPath
                            .withPath(customPath.getPath())
                            .withPathType(customPath.getPathType())
                            .withNewBackend()
                                .withNewService()
                                    .withName(customPath.getBackend().getService().getName())
                                    .withNewPort()
                                        .withNumber(customPath.getBackend().getService().getPort().getNumber())
                                    .endPort()
                                .endService()
                            .endBackend()
                        .build()
                    ).collect(Collectors.toList()))
                .endHttp()
                .build()
            ).collect(Collectors.toList());

        return new IngressBuilder()
            .withNewMetadata()
                .withName(ingressName)
                .withNamespace(resource.getMetadata().getNamespace())
                .withLabels(Map.of(
                    "app", resource.getMetadata().getName(),
                    "app.kubernetes.io/managed-by", "phee-importer-operator"
                ))            
                .withAnnotations(resource.getSpec().getIngress().getAnnotations()) 
                .withOwnerReferences(createOwnerReferences(resource))
            .endMetadata()
            .withNewSpec()
                .withIngressClassName(resource.getSpec().getIngress().getClassName())
                .withTls(ingressTlsList)
                .withRules(rules) // Ensure this is a List<IngressRule>
            .endSpec()
            .build();
    }

    private void reconcileConfigmap(PhEeImporterRdbms resource) {
        String name = resource.getMetadata().getName() + "-configmap";
        log.info("Reconciling ConfigMap for resource: {}", resource.getMetadata().getName());
        ConfigMap configMap = createConfigMap(resource, name);
        log.info("Created ConfigMap spec: {}", configMap);

        Resource<ConfigMap> configMapResource = kubernetesClient.configMaps()
                .inNamespace(resource.getMetadata().getNamespace())
                .withName(name);

        if (configMapResource.get() == null) {
            configMapResource.create(configMap);
            log.info("Created new ConfigMap: {}", name);
        } else {
            configMapResource.patch(configMap);
            log.info("Updated existing ConfigMap: {}", name);
        }
    }

    private ConfigMap createConfigMap(PhEeImporterRdbms resource, String name) {
        log.info("Creating ConfigMap spec for resource: {}", resource.getMetadata().getName());
        return new ConfigMapBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(resource.getMetadata().getNamespace())
                    .withOwnerReferences(createOwnerReferences(resource))
                .endMetadata()
                .addToData("config-file-name", "config-file-content") // Add actual config data
                .build();
    }

    // Reconcile Secret
    private void reconcileSecret(PhEeImporterRdbms resource) {
        String secretName = resource.getMetadata().getName() + "-secret";
        log.info("Reconciling Secret for resource: {}", resource.getMetadata().getName());
        Secret secret = createSecret(resource, secretName);
        log.info("Created Secret spec: {}", secret);

        Resource<Secret> secretResource = kubernetesClient.secrets()
                .inNamespace(resource.getMetadata().getNamespace())
                .withName(secretName);

        if (secretResource.get() == null) {
            secretResource.create(secret);
            log.info("Created new Secret: {}", secretName);
        } else {
            secretResource.patch(secret);
            log.info("Updated existing Secret: {}", secretName);
        }
    }

    private Secret createSecret(PhEeImporterRdbms resource, String secretName) {
        log.info("Creating Secret spec for resource: {}", resource.getMetadata().getName());
        return new SecretBuilder()
                .withNewMetadata()
                    .withName(secretName)
                    .withNamespace(resource.getMetadata().getNamespace())
                    .withOwnerReferences(createOwnerReferences(resource))
                .endMetadata()
                .addToData("database-password", Base64.getEncoder().encodeToString(resource.getSpec().getDatasource().getPassword().getBytes()))
                .build();
    }

    // Reconcile ServiceAccount
    private void reconcileServiceAccount(PhEeImporterRdbms resource) {
        String saName = resource.getMetadata().getName() + "-sa";
        log.info("Reconciling ServiceAccount for resource: {}", resource.getMetadata().getName());
        ServiceAccount serviceAccount = createServiceAccount(resource, saName);
        log.info("Created ServiceAccount spec: {}", serviceAccount);

        Resource<ServiceAccount> serviceAccountResource = kubernetesClient.serviceAccounts()
                .inNamespace(resource.getMetadata().getNamespace())
                .withName(saName);

        if (serviceAccountResource.get() == null) {
            serviceAccountResource.create(serviceAccount);
            log.info("Created new ServiceAccount: {}", saName);
        } else {
            serviceAccountResource.patch(serviceAccount);
            log.info("Updated existing ServiceAccount: {}", saName);
        }
    }

    private ServiceAccount createServiceAccount(PhEeImporterRdbms resource, String saName) {
        log.info("Creating ServiceAccount spec for resource: {}", resource.getMetadata().getName());
        return new ServiceAccountBuilder()
                .withNewMetadata()
                    .withName(saName)
                    .withNamespace(resource.getMetadata().getNamespace())
                    .withOwnerReferences(createOwnerReferences(resource))
                .endMetadata()
                .build();
    }

    // Reconcile Role
    private void reconcileRole(PhEeImporterRdbms resource) {
        String roleName = resource.getMetadata().getName() + "-role";
        log.info("Reconciling Role for resource: {}", resource.getMetadata().getName());
        Role role = createRole(resource, roleName);
        log.info("Created Role spec: {}", role);

        Resource<Role> roleResource = kubernetesClient.rbac().roles()
                .inNamespace(resource.getMetadata().getNamespace())
                .withName(roleName);

        if (roleResource.get() == null) {
            roleResource.create(role);
            log.info("Created new Role: {}", roleName);
        } else {
            roleResource.patch(role);
            log.info("Updated existing Role: {}", roleName);
        }
    }

    private Role createRole(PhEeImporterRdbms resource, String roleName) {
        log.info("Creating Role spec for resource: {}", resource.getMetadata().getName());
        return new RoleBuilder()
                .withNewMetadata()
                    .withName(roleName)
                    .withNamespace(resource.getMetadata().getNamespace())
                    .withOwnerReferences(createOwnerReferences(resource))
                .endMetadata()
                .addNewRule()
                    .withApiGroups("")
                    .withResources("pods", "services", "endpoints", "persistentvolumeclaims")
                    .withVerbs("get", "list", "watch", "create", "update", "patch", "delete")
                .endRule()
                .build();
    }

    // Reconcile RoleBinding
    private void reconcileRoleBinding(PhEeImporterRdbms resource) {
        String roleBindingName = resource.getMetadata().getName() + "-rolebinding";
        log.info("Reconciling RoleBinding for resource: {}", resource.getMetadata().getName());
        RoleBinding roleBinding = createRoleBinding(resource, roleBindingName);
        log.info("Created RoleBinding spec: {}", roleBinding);

        Resource<RoleBinding> roleBindingResource = kubernetesClient.rbac().roleBindings()
                .inNamespace(resource.getMetadata().getNamespace())
                .withName(roleBindingName);

        if (roleBindingResource.get() == null) {
            roleBindingResource.create(roleBinding);
            log.info("Created new RoleBinding: {}", roleBindingName);
        } else {
            roleBindingResource.patch(roleBinding);
            log.info("Updated existing RoleBinding: {}", roleBindingName);
        }
    }

    private RoleBinding createRoleBinding(PhEeImporterRdbms resource, String roleBindingName) {
        log.info("Creating RoleBinding spec for resource: {}", resource.getMetadata().getName());
        return new RoleBindingBuilder()
                .withNewMetadata()
                    .withName(roleBindingName)
                    .withNamespace(resource.getMetadata().getNamespace())
                    .withOwnerReferences(createOwnerReferences(resource))
                .endMetadata()
                .withSubjects(new SubjectBuilder()
                        .withKind("ServiceAccount")
                        .withName(resource.getMetadata().getName() + "-sa")
                        .withNamespace(resource.getMetadata().getNamespace())
                        .build())
                .withRoleRef(new RoleRefBuilder()
                        .withApiGroup("rbac.authorization.k8s.io")
                        .withKind("Role")
                        .withName(resource.getMetadata().getName() + "-role")
                        .build())
                .build();
    }

    // Reconcile ClusterRole
    private void reconcileClusterRole(PhEeImporterRdbms resource) {
        String clusterRoleName = resource.getMetadata().getName() + "-clusterrole";
        log.info("Reconciling ClusterRole for resource: {}", resource.getMetadata().getName());
        ClusterRole clusterRole = createClusterRole(resource, clusterRoleName);
        log.info("Created ClusterRole spec: {}", clusterRole);

        Resource<ClusterRole> clusterRoleResource = kubernetesClient.rbac().clusterRoles()
                .withName(clusterRoleName);

        if (clusterRoleResource.get() == null) {
            clusterRoleResource.create(clusterRole);
            log.info("Created new ClusterRole: {}", clusterRoleName);
        } else {
            clusterRoleResource.patch(clusterRole);
            log.info("Updated existing ClusterRole: {}", clusterRoleName);
        }
    }

    private ClusterRole createClusterRole(PhEeImporterRdbms resource, String clusterRoleName) {
        log.info("Creating ClusterRole spec for resource: {}", resource.getMetadata().getName());
        return new ClusterRoleBuilder()
                .withNewMetadata()
                    .withName(clusterRoleName)
                    .withOwnerReferences(createOwnerReferences(resource))
                .endMetadata()
                .addNewRule()
                    .withApiGroups("")
                    .withResources("pods", "services", "endpoints", "persistentvolumeclaims")
                    .withVerbs("get", "list", "watch", "create", "update", "patch", "delete")
                .endRule()
                .addNewRule()
                    .withApiGroups("apps")
                    .withResources("deployments")
                    .withVerbs("get", "list", "watch", "create", "update", "patch", "delete")
                .endRule()
                .build();
    }

    // Reconcile ClusterRoleBinding
    private void reconcileClusterRoleBinding(PhEeImporterRdbms resource) {
        String clusterRoleBindingName = resource.getMetadata().getName() + "-clusterrolebinding";
        log.info("Reconciling ClusterRoleBinding for resource: {}", resource.getMetadata().getName());
        ClusterRoleBinding clusterRoleBinding = createClusterRoleBinding(resource, clusterRoleBindingName);
        log.info("Created ClusterRoleBinding spec: {}", clusterRoleBinding);

        Resource<ClusterRoleBinding> clusterRoleBindingResource = kubernetesClient.rbac().clusterRoleBindings()
                .withName(clusterRoleBindingName);

        if (clusterRoleBindingResource.get() == null) {
            clusterRoleBindingResource.create(clusterRoleBinding);
            log.info("Created new ClusterRoleBinding: {}", clusterRoleBindingName);
        } else {
            clusterRoleBindingResource.patch(clusterRoleBinding);
            log.info("Updated existing ClusterRoleBinding: {}", clusterRoleBindingName);
        }
    }

    private ClusterRoleBinding createClusterRoleBinding(PhEeImporterRdbms resource, String clusterRoleBindingName) {
        log.info("Creating ClusterRoleBinding spec for resource: {}", resource.getMetadata().getName());
        return new ClusterRoleBindingBuilder()
                .withNewMetadata()
                    .withName(clusterRoleBindingName)
                    .withOwnerReferences(createOwnerReferences(resource))
                .endMetadata()
                .withSubjects(new SubjectBuilder()
                        .withKind("ServiceAccount")
                        .withName(resource.getMetadata().getName() + "-sa")
                        .withNamespace(resource.getMetadata().getNamespace())
                        .build())
                .withRoleRef(new RoleRefBuilder()
                        .withApiGroup("rbac.authorization.k8s.io")
                        .withKind("ClusterRole")
                        .withName(resource.getMetadata().getName() + "-clusterrole")
                        .build())
                .build();
    }

    private List<OwnerReference> createOwnerReferences(PhEeImporterRdbms resource) {
        // clusterRole and clusterRoleBinding can not be deleted using owner reference
        return Collections.singletonList(
            new OwnerReferenceBuilder()
                .withApiVersion(resource.getApiVersion())
                .withKind(resource.getKind())
                .withName(resource.getMetadata().getName())
                .withUid(resource.getMetadata().getUid())
                .withController(true)
                .withBlockOwnerDeletion(true)
                .build()
        );
    }
}