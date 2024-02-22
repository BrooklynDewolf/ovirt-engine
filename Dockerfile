# Use CentOS Stream 9 container image from quay.io as the base
FROM quay.io/ovirt/buildcontainer:el9stream

# Set environment variable for the artifacts directory
ENV ARTIFACTS_DIR=exported-artifacts

# Install required PyPI packages using pip3
# Ensure system packages are up to date and install createrepo_c for creating DNF repositories
RUN dnf -y --nobest update && \
    dnf install -y createrepo_c && \
    pip3 install "ansible-lint>=6.0.0,<7.0.0"

# Explicitly copy the current directory and the .git directory to /src in the container
COPY . /src

# Set the working directory to /src
WORKDIR /src
