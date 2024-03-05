# Use CentOS Stream 9 container image from quay.io as the base
FROM quay.io/ovirt/buildcontainer:el9stream

ARG USERNAME=build
ENV USERNAME=$USERNAME

# Install required PyPI packages using pip3
RUN dnf -y --nobest update && \
    dnf install -y sudo && \
    pip3 install "ansible-lint>=6.0.0,<7.0.0" && \
    echo $USERNAME ALL=\(root\) NOPASSWD:ALL > /etc/sudoers.d/$USERNAME

# Explicitly copy the current directory and the .git directory to /src in the container
COPY . /src

# Set the working directory to /src
WORKDIR /src

# Build Spec
RUN make ovirt-engine.spec

# Install builds deps
RUN dnf -y builddep ovirt-engine.spec

# Install run deps
RUN dnf -y install python3-daemon python3-otopi python3-psycopg2 python3-ovirt-setup-lib otopi-common initscripts-service bind-utils postgresql ovirt-engine-wildfly-overlay

# engine-setup needs the link to initctl
RUN ln -s /usr/sbin/service /usr/bin/initctl

# Set default User
USER $USERNAME

# Expose oVirt-Engine ports
EXPOSE 8080 8443