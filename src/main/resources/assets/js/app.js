var statusApp = angular.module('statusApp', []);

statusApp.controller('StatusCtrl', function ($scope, $http, $interval) {
    $scope.refresh = function() {
        $http.get('api/status').success(function(data) {
            $scope.status = data;
        });
    };

    $scope.refresh();

    $interval(function() {
        $scope.refresh();
    }, 1000);
});