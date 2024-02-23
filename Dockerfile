ARG USERNAME=build

# Use CentOS Stream 9 container image from quay.io as the base
FROM quay.io/ovirt/buildcontainer:el9stream

# Install required PyPI packages using pip3
RUN dnf -y --nobest update && \
    dnf install -y sudo && \
    pip3 install "ansible-lint>=6.0.0,<7.0.0" && \
    echo build ALL=\(root\) NOPASSWD:ALL > /etc/sudoers.d/build

# Explicitly copy the current directory and the .git directory to /src in the container
COPY . /src

# Set the working directory to /src
WORKDIR /src

# Build Spec
RUN make ovirt-engine.spec

# Install builds deps
RUN dnf builddep ovirt-engine.spec

# Install run deps
RUN dnf -y install python3-daemon otopi-common

# Symlink python to python3
RUN ln -s /usr/bin/python3 /usr/bin/python

# Set default User
USER $USERNAME