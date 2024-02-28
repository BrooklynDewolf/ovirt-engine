# oVirt Engine - Dev Container setup & building

There is already a `devcontainer.json` file present in the root directory of this repository, so it is not needed to configure this any different. You can configure this as a local devcontainer or remote devcontainer via Visual Studio Code. More info on how to setup a devcontainer can be found [here](https://code.visualstudio.com/docs/devcontainers/containers).

Make sure that tools like [git](https://git-scm.com/) and [Docker](https://www.docker.com/) are installed on the machine that the devcontainer is running on. 

### Build & connect to your Dev Container

#### Remote Dev Container

You can use the Remote Explorer to SSH to a remote destination server running Docker via the 'Remote Explorer' tab in VSC. This requires the "Remote - SSH" and "Remote Explorer" extensions from Microsoft.

### Building the application

The development environment can be built an installed with the following command;

```
make install-dev PREFIX=/home/build/ovirt/
```

This will start building the application, the tests can be skipped by adding the `SKIP_TESTS=1` flag after the previous command. The installation directory is specified with the `PREFIX` flag.

### Setup the PostgresQL database

Navigate to project directory outside of your Dev Container (if remote, SSH to your and navigate to where your source files of this project are stored). You can use the Docker compose file to fully setup your PostgresQL container with testing credentials by running:

```
docker compose up -d
```

The `-d` flag starts the container in detached mode. 

> Make sure to run this in the root directory of the project repository (and not in your Dev Container!).

### Run the oVirt Engine setup

If all the previous steps have been done correctly, it is time to setup your oVirt Engine. You can do this manually by running the following command:

```
/home/build/ovirt/bin/engine-setup --offline
```

However, it is also possible to setup the engine with standard values to speed up the process by adding the `--config=answers.config.in` flag to the command above. Make sure you are in the root directory of your project when using this flag.

The values entered by this config can be found in `answers.config.in` file.