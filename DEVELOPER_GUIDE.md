# DEVELOPER GUIDE

## Table of Contents

## How the Operator Works
1. [Key Components](#key-components)
   - [Custom Resource Definition (CRD) and Custom Resource (CR)](#custom-resource-definition-crd-and-custom-resource-cr)
     - Custom Resource Definition (CRD)
     - Custom Resource (CR)
   - [Controller File](#controller-file)
     - Purpose
     - Function
   - [Kind, Group, and Versioning](#kind-group-and-versioning)
     - Kind
     - Group
     - Versioning

## Explanation of Each File and Their Role
1. [CRD-CR](#crd-cr)
   - [Custom Resource Definition (CRD) Fields](#custom-resource-definition-crd-fields)
     - Metadata
     - Spec
     - Schema (openAPIV3Schema)
     - Status
2. [operator.yaml File](#operatoryaml-file)
3. [Java Files](#java-files)
   - [PhEeImporterRdbms.java File](#pheimporterrdbmsjava-file)
   - [PhEeImporterRdbmsSpec.java File](#pheimporterrdbmsspecjava-file)
4. [Utility Classes](#utility-classes)
   - [DeletionUtil.java File](#deletionutiljava-file)
   - [DeploymentUtils.java File](#deploymentutilsjava-file)
   - [LoggingUtil.java File](#loggingutiljava-file)
   - [NetworkingUtils.java File](#networkingutilsjava-file)
   - [OwnerReferenceUtils.java File](#ownerreferenceutilsjava-file)
   - [RbacUtils.java File](#rbacutilsjava-file)
   - [ResourceUtils.java File](#resourceutilsjava-file)
   - [StatusUpdateUtil.java File](#statusupdateutiljava-file)
5. [OperatorMain.java File](#operatormainjava-file)
6. [PhEeImporterRdbmsController.java File](#pheimporterrdbmscontrollerjava-file)

## deploy-operator.sh
1. [Key Functions](#key-functions)
   - Deploy Operator
   - Cleanup Operator
   - Update CR and Operator


## How the Operator Works

To start making changes to the PHEE Operator, it's crucial to understand several key components that define the architecture of the operator and how they interact. You can refer to the official documentation for further clarity, but here’s a brief explanation:

### Key Components

1. **Custom Resource Definition (CRD) and Custom Resource (CR)**

   - **Custom Resource Definition (CRD):** 
     The CRD is a schema that defines the structure and validation rules for custom resources. It acts as a blueprint specifying how custom resources should be formatted and what fields they should include.

   - **Custom Resource (CR):** 
     The CR is an instance of the CRD, representing a specific configuration of resources. Think of the CRD as a template or switchboard and the CR as the plug that fits into this switchboard. The CR must adhere to the schema defined by the CRD to ensure proper functionality. Essentially, the CR defines the desired state of resources that the operator should manage.

2. **Controller File**

   - **Purpose:** 
     The controller file is a Kubernetes component that watches for changes to custom resources and ensures that the cluster's state matches the desired state specified by the CR. It uses the values defined in the CR to create, update, or delete resources as needed.

   - **Function:** 
     The controller continuously monitors custom resources and triggers reconciliation processes to align the cluster's actual state with the desired state defined in the CR. If there are any changes in the CR, the controller invokes reconciliation methods to update the cluster accordingly.

3. **Kind, Group, and Versioning**

   - **Kind:** 
     The `kind` defines the type of resource, and it plays a crucial role in linking the CRD, CR, and controller. The CRD defines a kind, which must be used in the CR to establish a connection between the CR and the CRD. The controller uses this kind to identify and manage the custom resource.

   - **Group:** 
     The `group` categorizes resource types within different API versions. When the controller interacts with a CR, it checks the group and version specified in the CRD to ensure compatibility and perform appropriate API operations.

   - **Versioning:** 
     Versioning ensures that the API definitions are compatible and allows for the evolution of resource schemas over time. The controller uses the group and version information to perform CRUD operations on the resources as defined by the CRD.



## Explanation of Each File and Their Role

Starting with the deployment files:

### CRD-CR

Our CRD for the operator contains all the fields that our controller file might need to maintain the desired state of the cluster. It defines the structure and validation rules for the custom resources, ensuring that the custom resources adhere to the specified format and contain all necessary information for the operator to function correctly.

## Custom Resource Definition (CRD) Fields

### Metadata

**Metadata** contains essential information about the CRD, such as its `name`, which identifies the CRD within the Kubernetes API. This `name` is formatted as `<plural>.<group>`, where `plural` is the plural form of the resource name and `group` specifies the API group. The `metadata` section helps Kubernetes identify and manage the CRD.

### Spec

**Spec** defines the specifications and behavior of the custom resource. This includes:
- **Group**: The API group under which the CRD is categorized.
- **Names**: Specifies the `kind`, `listKind`, `plural`, `singular`, and optional `shortNames` for the custom resource.
- **Scope**: Indicates whether the CRD is `Namespaced` or `Cluster-wide`.
- **Versions**: Defines the versions of the CRD, including whether they are `served` and used for `storage`. It also specifies `subresources` like `status` and the `schema` for validation.

### Schema (openAPIV3Schema)

**Schema (openAPIV3Schema)** outlines the structure and validation rules for the custom resource's specification. It includes the `spec` and its fields, providing a detailed structure for how the custom resources are defined and validated. This block is crucial for ensuring that the custom resources conform to the specified format.

- **Spec** fields:
  - `enabled`
  - `volMount`
  - `replicas`
  - `image`
  - `containerPort`
  - `springProfilesActive`
  - `environment`
  - `datasource`
  - `resources`
  - `logging`
  - `javaToolOptions`
  - `bucketName`
  - `livenessProbe`
  - `readinessProbe`
  - `ingress`
  - `services`
  - `rbacEnabled`
  - `secretEnabled`
  - `configMapEnabled`
  - `ingressEnabled`

### Status

**Status** provides information about the state of the custom resource. It includes fields such as `availableReplicas`, `errorMessage`, `lastAppliedImage`, and `ready`. This section is used to track the current state and health of the resource, making it easier to monitor and manage its lifecycle.

## operator.yaml File

This YAML file defines several Kubernetes resources essential for deploying and managing the PHEE Importer Operator. It starts with a `ServiceAccount`, which is used by the operator to interact with the Kubernetes API. The `Deployment` specifies how the operator should be deployed, including the Docker image to use, resource requests and limits, environment variables, and the service account to associate with it. The `ClusterRole` and `ClusterRoleBinding` provide the operator with the necessary permissions to access and manage various Kubernetes resources across the cluster. The `Role` and `RoleBinding` are used to grant specific permissions within the `default` namespace, ensuring the operator can manage resources like custom resources, their statuses, and associated roles. Overall, this file configures the operator's runtime environment, access controls, and permissions, ensuring it operates correctly and securely within the Kubernetes cluster.

## PhEeImporterRdbms.java File

This Java file defines the custom resource for `PhEeImporterRdbms` in the Kubernetes ecosystem using the Fabric8 Kubernetes client. It extends the `CustomResource` class, which is part of the Fabric8 library, and implements the `Namespaced` interface to indicate that this custom resource is scoped to a namespace. The class is annotated with `@Version`, `@Group`, and `@Plural` to specify the API version, API group, and plural name of the custom resource, respectively. This setup allows the Kubernetes API to recognize and manage the `PhEeImporterRdbms` resource, including its specification and status, as defined by the `PhEeImporterRdbmsSpec` and `PhEeImporterRdbmsStatus` classes. This file is crucial for enabling Kubernetes to handle the custom resource and its associated data effectively.

## PhEeImporterRdbmsSpec.java File

The `PhEeImporterRdbmsSpec.java` file defines the specification for the `PhEeImporterRdbms` custom resource in Kubernetes. It includes fields that detail the configuration and operational parameters of the custom resource, such as `enabled`, `volMount`, `replicas`, `image`, and `containerPort`. This class serves as the blueprint for how the custom resource should be structured and what information it should contain. It provides getters and setters for each field, ensuring that the specification can be easily managed and accessed. The significance of this file lies in its role in specifying the desired state and configuration for the custom resource, which the Kubernetes controller will use to manage and reconcile the resource's state within the cluster. This file is essential for translating the custom resource's desired state into a format that Kubernetes can understand and act upon.

## DeletionUtil.java File

The `DeletionUtil.java` file is a utility class designed for managing the deletion of Kubernetes resources associated with a custom resource of type `PhEeImporterRdbms`. It provides methods to delete various Kubernetes resources such as Deployments, RBAC-related resources (ServiceAccounts, Roles, RoleBindings, ClusterRoles, and ClusterRoleBindings), Secrets, ConfigMaps, and Ingresses. The significance of this file lies in its ability to clean up all related resources efficiently when a custom resource is no longer needed, thereby maintaining a clean and organized Kubernetes environment. The utility ensures that all associated resources are properly removed, preventing resource leaks and potential conflicts within the cluster.

## DeploymentUtils.java File

The `DeploymentUtils.java` file is a utility class that provides methods to assist in the creation and configuration of Kubernetes deployments based on a custom resource of type `PhEeImporterRdbms`. It includes methods to generate environment variables, resource requirements, and probes for Kubernetes deployments. Specifically, `createEnvironmentVariables` builds a list of environment variables for the deployment, handling both direct values and those sourced from secrets. `createResourceRequirements` creates resource constraints for CPU and memory based on the custom resource specifications. The `createProbe` method generates liveness or readiness probes according to the specifications provided in the custom resource. This file is significant because it streamlines the process of translating custom resource configurations into Kubernetes deployment specifications, ensuring consistency and efficiency in deployment management.

## LoggingUtil.java File

The `LoggingUtil.java` file is a utility class designed to handle logging of details related to the `PhEeImporterRdbms` custom resource. It includes methods for logging comprehensive details about the custom resource, such as its general configuration, datasource settings, resource limits and requests, and logging configurations. The `logResourceDetails` method logs various aspects of the custom resource, including the number of replicas, container image, datasource configuration, resource constraints, and logging settings. It also provides warnings if certain configurations are missing. The `logError` method is used to log error messages along with exception stack traces. This file is significant because it provides structured and detailed logging, which is essential for debugging, monitoring, and maintaining transparency in the operation of the custom resource.


## NetworkingUtils.java File

The `NetworkingUtils.java` file is a utility class responsible for managing Kubernetes networking resources, specifically Services and Ingresses, for the `PhEeImporterRdbms` custom resource. It provides methods for reconciling these resources, which includes creating, updating, or deleting Services and Ingresses based on the custom resource's specifications. The `reconcileServices` method handles the synchronization of Kubernetes Service objects, while the `reconcileIngress` method manages the Ingress objects, ensuring they match the desired state defined in the custom resource. The `createServices` and `createIngress` methods are used to construct the appropriate Kubernetes resources from the specifications provided. This file is significant because it encapsulates the logic required to keep networking configurations up to date, ensuring that the services and ingress rules for the custom resource are correctly maintained and aligned with the specified configurations.

## OwnerReferenceUtils.java File

The `OwnerReferenceUtils.java` file contains a utility class that provides a method for creating Kubernetes OwnerReferences for a given custom resource, specifically `PhEeImporterRdbms`. The `createOwnerReferences` method generates a list containing a single `OwnerReference` that links the resource to its owner, ensuring that the resource's lifecycle is managed in relation to its owner. This is crucial for ensuring that resources are properly cleaned up when their owners are deleted. The method sets the `controller` flag to `true` and `blockOwnerDeletion` to `true`, which ensures that the resource is not deleted until its owner is also deleted, except for ClusterRole and ClusterRoleBinding which cannot be managed this way. This file is significant because it centralizes the logic for managing ownership and cleanup dependencies between Kubernetes resources, which is essential for maintaining resource integrity and preventing orphaned resources.


## RbacUtils.java File

The `RbacUtils.java` file provides utility functions for managing Kubernetes RBAC (Role-Based Access Control) resources associated with a custom resource, specifically `PhEeImporterRdbms`. It contains methods for reconciling various RBAC components including `ServiceAccount`, `Role`, `RoleBinding`, `ClusterRole`, and `ClusterRoleBinding`. Each method ensures that the respective RBAC resource exists and is up-to-date according to the specifications provided by the custom resource. This file is significant because it centralizes and standardizes the process of creating and updating RBAC resources, which are essential for managing access permissions and security within a Kubernetes cluster. By using these utilities, the custom resource can maintain appropriate access controls and permissions dynamically, ensuring that the resources are correctly configured and associated with the appropriate roles and permissions.

## ResourceUtils.java File

The `ResourceUtils.java` file provides utility functions for managing Kubernetes `ConfigMap` and `Secret` resources associated with the custom resource `PhEeImporterRdbms`. It includes methods for reconciling these resources by creating or updating them based on the specifications provided in the custom resource. The `reconcileConfigmap` and `reconcileSecret` methods ensure that the corresponding Kubernetes resources are present and correctly configured, while `createConfigMap` and `createSecret` methods define the structure and data of these resources. This file is significant because it centralizes the logic for managing configuration and sensitive data in Kubernetes, ensuring that the `ConfigMap` and `Secret` are properly synchronized with the custom resource and its specifications, thereby enhancing the reliability and maintainability of the deployment process.

## StatusUpdateUtil.java File

The `StatusUpdateUtil.java` file provides utility methods for updating the status of the `PhEeImporterRdbms` custom resource in a Kubernetes cluster. It includes methods for updating the status with details such as the number of available replicas, the last applied image, readiness, and error messages. The `updateStatus` method ensures that the status is correctly applied and reflects the current state of the resource, while `updateErrorStatus` and `updateDisabledStatus` handle specific cases of errors or disabled resources, respectively. This file is significant because it centralizes the logic for status management, ensuring that the custom resource's state is accurately represented and maintained, which is crucial for effective monitoring and troubleshooting within Kubernetes.

## OperatorMain.java File

The `OperatorMain.java` file is the entry point for starting the Payment Hub EE Operator. It sets up the Kubernetes client, initializes the operator, and begins the reconciliation process. The `main` method creates a Kubernetes client using `KubernetesClientBuilder`, initializes an `Operator` instance, and registers the `PhEeImporterRdbmsController` as the reconciler. The operator is then started, and the application runs indefinitely, handling custom resource reconciliation. This file is significant because it orchestrates the setup and execution of the operator, ensuring that the custom resource management is performed continuously and reliably within the Kubernetes environment.

## PhEeImporterRdbmsController.java File

The `PhEeImporterRdbmsController.java` file implements the core reconciliation logic for managing the `PhEeImporterRdbms` custom resource in Kubernetes. This controller is responsible for ensuring that the state of Kubernetes resources such as RBAC, Secrets, ConfigMaps, Ingress, Services, and Deployments matches the specifications defined in the custom resource. It uses various utility classes to handle specific aspects of resource management, including RBAC, networking, and resource creation. The file contains methods to reconcile and manage these resources, handle errors, and update the status of the custom resource accordingly. Its significance lies in its role in automating the deployment and management of Kubernetes resources, ensuring consistency and alignment with the desired state defined in the custom resource.

# deploy-operator.sh

The `deploy-operator.sh` script is designed to manage the deployment and cleanup of a Kubernetes operator. It facilitates several key operations through command-line arguments: deploying the operator, cleaning up deployed resources, and updating both the Custom Resource (CR) and the operator itself. 

### Key Functions:
1. **Deploy Operator**: This function handles the entire deployment process, including checking pre-requisites, building the Java project, creating a Docker image, importing it into a k3s cluster, and applying necessary Kubernetes resources like CRD (Custom Resource Definition), the operator itself, and the CR. It also verifies the successful deployment of the operator.
   
2. **Cleanup Operator**: This function removes all deployed resources including the CR, operator, and CRD. It also deletes the locally built Docker image and the associated TAR file to ensure that no residual files are left.

3. **Update CR and Operator**: These functions allow for updating the Custom Resource and the operator deployment respectively, ensuring that changes can be applied without a full redeployment.

The script uses color-coded text for clear and informative feedback during execution. It is crucial for maintaining and managing the lifecycle of the Kubernetes operator, ensuring both smooth deployments and efficient cleanup when resources are no longer needed.




