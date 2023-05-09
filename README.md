# oci-bridge



## Introduction

This is a simple tool intended to help Bridge event sources and destinations where there isn't a natural connection deployment already available within OCI.  It has been initially developed to support connecting [OCI Queues](https://www.oracle.com/cloud/queue/) to and from [Solace](https://solace.com/) [PubSub+](https://solace.com/products/event-broker/software/) but creating new sources or destinations is straightforward.

The configuration is driven via environment property values, making it simple to deploy within containers - no external configuration files are needed.



## Getting Started

All configuration values are case-sensitive.

We have provided a simple script to support running the utility locally, which will set up the environment variables needed for a simple configuration.

### Properties

#### Core

| Env Var Name          | Description                                                  | Example Value |
| --------------------- | ------------------------------------------------------------ | ------------- |
| isMultiPass           | Tells the tool whether to make single pass through all the connections of all of them and repeat. | True          |
| milliDelayOnMultiPass |                                                              |               |
| ListConnectorProps    |                                                              |               |
| ConnectionList        |                                                              |               |

The following provides details of the different properties needed and what they do. With the exception of the core properties, the configuration values need to be prefixed with the connection name, which is used to trace the configuration values to the connections in the `ListConnectorProps`. For example

`ListConnectionProperties=Synthetic1-Synthetic2`

`Synthetic1__Type=Synthetic`

We use a double underscore to separate the connection name and the attribute for that connection's configuration.

##### Common Connection Attributes

| Env Var Name | Description                                                  | Example Value |
| ------------ | ------------------------------------------------------------ | ------------- |
| Type         | Defines the connection type that is being represented, Currently accepted values are: Synthetic, Solace, OCIQUEUE. Each type of connection must have a unique type name. | Synthetic     |

#### OCI Queue

| Env Var Name     | Description | Example Value |
| ---------------- | ----------- | ------------- |
| OCI_AUTHFILE     |             |               |
| OCI_USERID       |             |               |
| OCI_TENANT_ID    |             |               |
| OCI_FINGERPRINT  |             |               |
| OCI_REGION       |             |               |
| OCI_QUEUEID      |             |               |
| POLLDURATIONSECS |             |               |
| QUEUENAME        |             |               |

#### Solace Pub/Sub+

Where ever possible, we try to propagate the Solace configuration values through to Solace Pub/Sub+. However, there are a couple of constraints.

- Solace uses a dot notation within some of its properties. As this can present issues with some OSes for environment variables, we have implemented a substitution mechanism. So within the Solace variable names, if a dot (.) is used, then for the environment variables, replace it with an underscore  (_)

| Env Var Name                           | Description | Example Value |
| -------------------------------------- | ----------- | ------------- |
| SOLACE_PORT                            |             |               |
| host                                   |             |               |
| MESSAGE_TYPE                           |             |               |
| solace_messaging_authentication_scheme |             |               |
| username                               |             |               |
| password                               |             |               |
| SOLACE_TOPICNAME                       |             |               |
| vpn_name                               |             |               |

#### Synthetic

The synthetic configuration doesn't need any parameters.

# Contributing

This project is open source. Please submit your contributions by forking this repository and submitting a pull request! Oracle appreciates any contributions that are made by the open-source community.

## License

Copyright (c) 2022 Oracle and/or its affiliates.

Licensed under the Universal Permissive License (UPL), Version 1.0.

See [LICENSE](https://github.com/oracle-devrel/terraform-oci-arch-tags/blob/main/LICENSE) for more details.

ORACLE AND ITS AFFILIATES DO NOT PROVIDE ANY WARRANTY WHATSOEVER, EXPRESS OR IMPLIED, FOR ANY SOFTWARE, MATERIAL OR CONTENT OF ANY KIND CONTAINED OR PRODUCED WITHIN THIS REPOSITORY, AND IN PARTICULAR SPECIFICALLY DISCLAIM ANY AND ALL IMPLIED WARRANTIES OF TITLE, NON-INFRINGEMENT, MERCHANTABILITY, AND FITNESS FOR A PARTICULAR PURPOSE. FURTHERMORE, ORACLE AND ITS AFFILIATES DO NOT REPRESENT THAT ANY CUSTOMARY SECURITY REVIEW HAS BEEN PERFORMED WITH RESPECT TO ANY SOFTWARE, MATERIAL OR CONTENT CONTAINED OR PRODUCED WITHIN THIS REPOSITORY. IN ADDITION, AND WITHOUT LIMITING THE FOREGOING, THIRD PARTIES MAY HAVE POSTED SOFTWARE, MATERIAL OR CONTENT TO THIS REPOSITORY WITHOUT ANY REVIEW. USE AT YOUR OWN RISK.
