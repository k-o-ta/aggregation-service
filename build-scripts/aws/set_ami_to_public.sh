#!/bin/bash
# Copyright 2022 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

#######################################
# Set AMI visibility to public.
# Applies to latest created AMI with given name prefix.
# Used by Github automated release build
# Arguments:
#   $1, AMI name prefix e.g. aggregation-service-enclave_0.1.0
#   $2, region
#######################################
set_ami_to_public_by_prefix() {
  ami=$(aws ec2 describe-images --filters \
  "Name=name,Values=${1}*" --region "$2" \
  --query "reverse(sort_by(Images, &CreationDate))[:1].{id: ImageId}" \
  --output text)

  aws ec2 modify-image-attribute \
    --image-id ${ami} \
    --region "$2" \
    --launch-permission "Add=[{Group=all}]"
}

"$@"
