# oVirt Engine - Dev Container setup & building

There is already a `devcontainer.json` file present in the root directory of this repository, so it is not needed to configure this any different. You can configure this as a local devcontainer or remote devcontainer via Visual Studio Code. More info on how to setup a devcontainer can be found [here](https://code.visualstudio.com/docs/devcontainers/containers).

Make sure that tools like [git](https://git-scm.com/) and [Docker](https://www.docker.com/) are installed on the machine that the devcontainer is running on. 

### Setup the PostgresQL database

Navigate to the project directory (if remote, SSH to your and navigate to where your source files of this project are stored). You can use the Docker compose file to fully setup your PostgresQL container with testing credentials by running:

```
docker compose up -d
```

The `-d` flag starts the container in detached mode. 

> It is important that the compose file is run before creating the dev container, otherwise there will be Docker networking issues.

### Build & connect to your Dev Container

#### Remote Dev Container

You can use the Remote Explorer to SSH to a remote destination server running Docker via the 'Remote Explorer' tab in VSC. This requires the "Remote - SSH" and "Remote Explorer" extensions from Microsoft.

### Building the application

The development environment can be built an installed with the following command;

```
make install-dev PREFIX=/home/build/ovirt/
```

This will start building the application, the tests can be skipped by adding the `SKIP_TESTS=1` flag after the previous command. The installation directory is specified with the `PREFIX` flag.


### Run the oVirt Engine setup

If all the previous steps have been done correctly, it is time to setup your oVirt Engine. You can do this manually by running the following command:

```
/home/build/ovirt/bin/engine-setup --offline --config=answers.config.in
```

However, it is also possible to setup the engine with standard values to speed up the process by adding the `--config=answers.config.in` flag to the command above. Make sure you are in the root directory of your project when using this flag.

The values entered by this config can be found in `answers.config.in` file.

### Running the engine itself

First, you need to set the ulimit of your environment (https://pagure.io/python-daemon/issue/40):

```
ulimit -n 2048
```

You can start the engine by running the `ovirt-engine.py` file with start argument to start the engine:
```
/home/build/ovirt/share/ovirt-engine/services/ovirt-engine/ovirt-engine.py start
```

### Uploading images via `ovirt-imageio`

In order to upload and download images via the administration portal, you will need to run the ovirt-imageio daemon. You can do this by executing the following command:
```
ovirt-imageio --conf-dir /home/build/ovirt/etc/ovirt-imageio
```

> If you are getting a certificate error when testing the connection, you will need to accept the certificate in your browser. You can either find the URL yourself by going to your network console in your browser, clicking the 'Test Connection' button and visiting the url that is giving an error OR you can simply navigate to `https://HOSTNAME:54323/info/` and accept the certificate.

### Connecting via console to your VM instance

In order to connect to the VM console via noVCN you will need to start the websocket service. However, it is possible that launching the service without setting a max files open limit will cause a memory leak, so we set a max open file limit.

```
ulimit -n 2048
```

Now we can safely boot the websocket service:

```
/home/build/ovirt/share/ovirt-engine/services/ovirt-websocket-proxy/ovirt-websocket-proxy.py start
```

Next, go to your newly created VM and click on the 'v' arrow next to console. Navigate to 'Console Options' and set 'Console Invocation' to 'noVCN'. You can save this and click on the 'Console' button.

You will see an error: "Something went wrong, connection is closed", this is normal. You need to accept the SSL certificate in your browser. You can do this by opening your browser development console and copying the `wss://XXXXX` url to your browser URL bar. Replace `wss` with `https` and navigate to the url. You can accept the certificate here. You can now reopen the console via the oVirt administrator panel.


