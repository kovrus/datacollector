/**
 * Copyright 2015 StreamSets Inc.
 *
 * Licensed under the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Controller for Stop Confirmation Modal.
 */

angular
  .module('dataCollectorApp.home')
  .controller('StopConfirmationModalInstanceController', function ($scope, $modalInstance, pipelineInfo, api, $q) {
    angular.extend($scope, {
      common: {
        errors: []
      },
      pipelineInfo: pipelineInfo,
      stopping: false,
      isList: _.isArray(pipelineInfo),

      yes: function() {
        $scope.stopping = true;
        if ($scope.isList) {
          var deferList = [];

          angular.forEach(pipelineInfo, function(pipeline) {
            deferList.push(api.pipelineAgent.stopPipeline(pipeline.name, 0));
          });

          $q.all(deferList)
            .then(
              function(results) {
                $modalInstance.close(results);
              },
              function () {
                $scope.stopping = false;
                $scope.common.errors = [data];
              }
            );

        } else {
          api.pipelineAgent.stopPipeline(pipelineInfo.name, 0)
            .success(function(res) {
              $modalInstance.close(res);
            })
            .error(function(data) {
              $scope.stopping = false;
              $scope.common.errors = [data];
            });
        }
      },
      no: function() {
        $modalInstance.dismiss('cancel');
      }
    });
  });
