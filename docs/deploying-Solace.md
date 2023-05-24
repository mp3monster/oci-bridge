# Introduction

This details how to get a simple deployment of [Solace PubSub+](https://solace.com/products/event-broker/software/) running on OCI [Oracle 8 Linux](https://docs.oracle.com/en-us/iaas/images/oracle-linux-8x/) VM. To minimize the amount of work involved in setup, we execute sufficient changes so that the standard PubSub+ Docker image will execute.

This isn't the only way to deploy  [Solace PubSub+](https://solace.com/products/event-broker/software/). Other options exist, such as Kubernetes or Container Instances deployments, but the I/O performance can be variable depending upon the configuration of the persistence layer. When developing a production deployment, I/O performance is essential - so minimizing abstraction layers to the storage medium is beneficial.

# Solace on a VM

The easiest way to set up a Solace deployment for proof of concept and demo arrangements is through the use of a VM. The following section describes the steps to achieve that.

## Creating the VM

The exact configuration of the Compute Instance depends upon the deployment needs of Solace, and the Solace documentation gives good guidance on this here. But for a basic deployment, the defaults for a Flex3 or Flex4 Oracle 8 Linux VM will be plentiful (and well above requirements for a typical development setup).

By default, Oracle Linux 8 OS expects the keys used to have a passphrase, so it is easier to upload your own key file - ensuring you've configured the passphrase. e.g.

`ssh-keygen -f solaceVM -p password <password>`

As the typical Solace use case on OCI will likely support multi-cloud or hybrid use cases, we need Solace to have a Public IP or a proxy layer (with a public IP) such as a WAF or Load Balancer.

Once the VM is set up, we must log in to the machine using *ssh*. This can be easily done from the OCI Console shell. e.g.

`ssh -i <keyfile> opc@my.ip.xx.yy`

## Setup the VM

We need to install the correct container runtimes and Docker service to enable the running of the Solace docker image. All the commands need to be performed with elevated permissions, hence the `sudo` '*prefix*' to all the commands.

The following commands will install some necessary utilities that will be needed, then pull a Docker Community Edition and remove *runc* from the OS. Once installed, we need to enable Docker as a service and start the service. Depending upon the spec of the VM. This will likely take a couple of minutes to execute.

``` 
sudo dnf install -y dnf-utils zip unzip
sudo dnf config-manager --add-repo=https://download.docker.com/linux/centos/docker-ce.repo

sudo dnf remove -y runc
sudo dnf install -y docker-ce --nobest

sudo systemctl enable docker.service
sudo systemctl start docker.service
```

With Docker ready to be used, we can get Docker to install the image and start it up with the following command:
```
docker run -d -p 8080:8080 -p 55555:55555 -p:80:80 --shm-size=2g --env username_admin_globalaccesslevel=admin --env username_admin_password=admin --name=solacePSPlusStandard solace/solace-pubsub-standard
```
There are a couple of points to observe with this command:

- We are providing the admin portal with its administrator credentials. For a production setup, you are better off connecting this to OCI's IAM using OAuth (see [here](https://docs.solace.com/Admin/Configuring-OAuth-for-Management-Access.htm)).
- *--shm* is setting the shared memory capacity. This figure is important as Solace uses shared memory as an important inter-process communications channel and needs a minimum of 1GB. It gets expressly defined because Docker defaults the value to 64MB.
- The port mapping (*-p*) may need to be extended depending on which protocols need to be supported with Solace. A complete list of the ports can be found [here](https://docs.solace.com/Admin/Default-Port-Numbers.htm).

With this configuration, we need to extend the network security rules to allow traffic through to these ports (more details [here](https://docs.oracle.com/en-us/iaas/Content/Network/Concepts/securityrules.htm#Security_Rules)).

# Deploying with Container Instances

[Container Instances](https://www.oracle.com/cloud/cloud-native/container-instances/) represent a convenient way to deploy applications, particularly when we're not too concerned about dynamic scaling. While deploying a Docker image is easy with Container Instances, it doesn't offer the opportunity to define the shared memory resource, which, as noted in the VM deployment, needs to be at least 1GB, but Docker defaults this to 64MB.

If you're comfortable editing the Dockerfile for the deployment, then this restriction can be overcome by incorporating into the Dockerfile a layer that remounts the *shm* resource, setting the correct size - you can see more about this constraint [here](https://github.com/docker/cli/issues/1278).

# Accessing the Console

With this, it should be possible to login into the Solace console using the VM's public IP (or the proxy layer's IP if used) on port *8080,* i.e., https://my.ip.a.b:8080 using the user name of *admin* and the password provided on the Docker run command.
