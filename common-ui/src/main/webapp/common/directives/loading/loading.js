/**
 * Loading Directive
 */

angular.module('showLoadingDirectives', [])
  .directive('showLoading', function() {
    'use strict';
    return {
      restrict: 'A',
      replace: true,
      scope: {
        loading: '=showLoading'
      },
      templateUrl: 'common/directives/loading/loading.tpl.html'
    };
  });