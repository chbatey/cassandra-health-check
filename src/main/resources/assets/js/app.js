var statusApp = angular.module('statusApp', []);

statusApp.controller('StatusCtrl', function ($scope, $http, $interval) {

    $scope.requests = {mean_rate: 0};
    $scope.refresh = function() {
        $http.get('api/status').success(function(data) {
            $scope.status = data;
        });
        $http.get('metrics').success(function(data) {
            $scope.requests = data.meters["cassandra-requests"];
        });
    };

    $scope.refresh();

    $interval(function() {
        $scope.refresh();

    }, 1000);
});