# PHEE Operator Local Setup

This guide provides instructions to set up the PHEE Operator in a k3s cluster using a provided script. The script simplifies the process of deploying, cleaning up, and updating the operator and its resources.

### Note
This is a Kubernetes (K8s) Operator setup built on top of the [Mifos-Gazelle script](https://github.com/openMF/mifos-gazelle). The operator is currently configured to deploy twelve deployments (with their ingress and service if needed) under the paymenthub deployment. The repository is actively being developed and tested to support more Mifos artifacts using the operator.

## Prerequisites

- Mifos-Gazelle script environment setup [here](https://github.com/openMF/mifos-gazelle).
- Maven, JDK, kubectl, and Docker installed. The script covers the installation of these, but you may still want to check out the official documentation if needed.
- Operator script file (`deploy-operator.sh`).

## Setup and Deployment

To perform various operations, the script `deploy-operator.sh` supports multiple modes. 
The script supports the following flags:
- `-m Flag`: This is used to specify the mode of operation, such as deploy or cleanup.
- `-u Flag`: This is used to specify the update mode, such as updating the cr (Custom Resource) or the operator deployment.

Below are the available commands:

### 1. Deploy the Operator

To build, deploy, and verify the operator and its required deployments using the CR in your k3s cluster:

```
./deploy-operator.sh -m deploy
```

### 2. Clean Up the Operator

To remove the operator and related resources from the k3s cluster:

```
./deploy-operator.sh -m cleanup
```

### 3. Update the Custom Resource (CR)

If you need to apply updates to the Custom Resource (CR):

```
./deploy-operator.sh -u cr
```
or can also run `kubectl apply -f deploy/cr/ph-ee-importer-rdbms-cr.yaml` in the operator directory.

### 4. Update the Operator Deployment

To apply updates to the operator deployment:

```
./deploy-operator.sh -u operator
```

## Usage Information

- Ensure the script is executable. If not, run `chmod +x deploy-operator.sh` to make it executable.
- The script should be run from the directory where it is located.
- The `deploy` mode will upgrade the Helm chart, build the Docker image, deploy the operator with its CRD and CR, and verify its status in the k3s cluster.
- The `cleanup` mode will remove the operator and all its related resources, allowing for a fresh setup if needed.
- The `CR` and `operator` update modes allow you to apply updates specifically to the CR or the operator deployment, respectively, without a full redeployment.

### Note
This file is still in progress will be updated as the project progresses.
Also, currently operator is configured for 12 deployments only, and not yet tested.
