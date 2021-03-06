/* global by, element */
'use strict';

// var SCREENSHOT = require('./screenshot');
var authentication = require('../authentication/authentication');
var common = require('../common/common');
var rolesCommon = require('../common/roles_common');
var cloudImagesCommon = require('./cloud_image');
var navigation = require('../common/navigation');
var genericForm = require('../generic_form/generic_form');


// Create and enable a default cloud
var beforeWithCloud = function() {
  common.before();
  authentication.login('admin');
  goToCloudList();
  createNewCloud('testcloud');
  goToCloudDetail('testcloud');
  enableCloud();
  authentication.logout();

};
module.exports.beforeWithCloud = beforeWithCloud;

var goToCloudList = function() {
  navigation.go('main', 'admin');
  navigation.go('admin', 'clouds');
};
module.exports.goToCloudList = goToCloudList;

var createNewCloud = function(newCloudName) {
  browser.element(by.id('new-cloud-button')).click();
  browser.waitForAngular();

  element(by.id('cloud-name-id')).sendKeys(newCloudName);
  var paaSProviderOption = element(by.css('select option:nth-child(2)'));
  paaSProviderOption.click();

  var btnCreate = browser.element(by.id('new-cloud-create-button'));
  // SCREENSHOT.takeScreenShot('cloud-common-create-' + newCloudName);
  browser.actions().click(btnCreate).perform();
  browser.waitForAngular();
};
module.exports.createNewCloud = createNewCloud;

var goToCloudDetail = function(cloudName) {
  goToCloudList();
  browser.element(by.name(cloudName)).click();
  browser.waitForAngular();
};
module.exports.goToCloudDetail = goToCloudDetail;

var cloneCloud = function(cloudName) {
  goToCloudList();
  browser.element(by.id('clone_cloud_' + cloudName)).click();
  browser.waitForAngular();
};
module.exports.cloneCloud = cloneCloud;

var enableCloud = function() {
  browser.element(by.id('cloud-enable-button')).click();
  browser.waitForAngular();
};
module.exports.enableCloud = enableCloud;

var disableCloud = function() {
  browser.element(by.id('cloud-disable-button')).click();
  browser.waitForAngular();
};
module.exports.disableCloud = disableCloud;

// select the cloud plugin by name
var selectApplicationCloud = function(pluginName) {
  var selectElement = element(by.id('cloud-select'));
  var selectResult = common.selectDropdownByText(selectElement, pluginName, 1);
  return selectResult; // promise
};
module.exports.selectApplicationCloud = selectApplicationCloud;

var selectDeploymentPluginCount = function() {
  var count = common.selectCount('cloud-select');
  return count; // promise
};
module.exports.selectDeploymentPluginCount = selectDeploymentPluginCount;

var deleteCloud = function() {
  common.deleteWithConfirm('btn-cloud-delete', true);
  browser.waitForAngular();
  browser.getCurrentUrl().then(function(url) {
    expect(common.getUrlElement(url, 1)).toEqual('admin');
    expect(common.getUrlElement(url, 2)).toEqual('clouds');
  });
};
module.exports.deleteCloud = deleteCloud;

// Check the cloud error and close the confirmAction popup if displayed, only woek after a deleteCloud()
var checkCloudError = function(displayed) {
  if (displayed === true) {
    browser.sleep(1000); // Technical sleep : wait the popover
    expect(element(by.id('force-cloud-disable-button')).isDisplayed()).toBe(displayed);
    common.confirmAction(false);
  }
};
module.exports.checkCloudError = checkCloudError;

var checkDeploymentsDisplayed = function(applicationsNames, displayed) {
  var deploymentsDiv = element(by.id('deployments-div'));
  if (applicationsNames) {
    if (Array.isArray(applicationsNames)) {
      applicationsNames.forEach(function(appName) {
        expect(deploymentsDiv.element(by.name('app_' + appName)).isDisplayed()).toBe(displayed);
      });
    } else {
      expect(deploymentsDiv.element(by.name('app_' + applicationsNames)).isDisplayed()).toBe(displayed);
    }
  }
};
module.exports.checkDeploymentsDisplayed = checkDeploymentsDisplayed;

function selectRightsTabCloud(cloudName, userType, userName, cloudRole) {
  // log as admin
  authentication.reLogin('admin');
  goToCloudList();
  goToCloudDetail(cloudName);
  // go to authorization tab
  element(by.id('rights-tab')).element(by.tagName('a')).click();
  browser.waitForAngular();

  // choose the good tab : users or groups
  element(by.id(userType)).element(by.tagName('a')).click();
  browser.waitForAngular();
  if (userType === 'users-tab') {
    rolesCommon.editUserRole(userName, cloudRole);
  } else { // groups-tab
    rolesCommon.editGroupRole(userName, cloudRole);
  }
}

// Toggle (grant/remove) rights on a cloud for a user (log as admin first)
var giveRightsOnCloudToUser = function(cloudName, user, cloudRole) {
  selectRightsTabCloud(cloudName, 'users-tab', user, cloudRole);
};
module.exports.giveRightsOnCloudToUser = giveRightsOnCloudToUser;

// Toggle (grant/remove) rights on a cloud for a group (log as admin first)
var giveRightsOnCloudToGroup = function(cloudName, group, cloudRole) {
  selectRightsTabCloud(cloudName, 'groups-tab', group, cloudRole);
};
module.exports.giveRightsOnCloudToGroup = giveRightsOnCloudToGroup;

var goToCloudDetailImage = function() {
  element(by.id('tab-clouds-image')).element(by.tagName('a')).click();
};
module.exports.goToCloudDetailImage = goToCloudDetailImage;

var goToCloudDetailFlavor = function() {
  element(by.id('tab-clouds-flavor')).element(by.tagName('a')).click();
};
module.exports.goToCloudDetailFlavor = goToCloudDetailFlavor;

var goToCloudDetailNetwork = function() {
  element(by.id('tab-clouds-network')).element(by.tagName('a')).click();
};
module.exports.goToCloudDetailNetwork = goToCloudDetailNetwork;

var goToCloudDetailStorage = function() {
  element(by.id('tab-clouds-storage')).element(by.tagName('a')).click();
};
module.exports.goToCloudDetailStorage = goToCloudDetailStorage;

var goToCloudDetailTemplate = function() {
  element(by.id('tab-clouds-template')).element(by.tagName('a')).click();
};
module.exports.goToCloudDetailTemplate = goToCloudDetailTemplate;

var goToCloudConfiguration = function() {
  element(by.id('tab-clouds-configuration')).element(by.tagName('a')).click();
};
module.exports.goToCloudConfiguration = goToCloudConfiguration;

var countCloud = function() {
  return element.all(by.repeater('cloud in data.data')).count();
};
module.exports.countCloud = countCloud;

var countImageCloud = function() {
  return element.all(by.repeater('cloudImageId in cloud.images')).count();
};
module.exports.countImageCloud = countImageCloud;

var countFlavorCloud = function() {
  return element.all(by.repeater('flavor in cloud.flavors')).count();
};
module.exports.countFlavorCloud = countFlavorCloud;

var countNetworkCloud = function() {
  return element.all(by.repeater('network in cloud.networks')).count();
};
module.exports.countNetworkCloud = countNetworkCloud;

var countTemplateCloud = function() {
  return element.all(by.repeater('template in cloud.computeTemplates')).count();
};
module.exports.countTemplateCloud = countTemplateCloud;

var countStorageCloud = function() {
  return element.all(by.repeater('storage in cloud.storages')).count();
};
module.exports.countStorageCloud = countStorageCloud;

var addNewFlavor = function(name, numCPUs, diskSize, memSize) {
  goToCloudDetailFlavor();
  browser.element(by.id('clouds-flavor-add-button')).click();
  genericForm.sendValueToPrimitive('id', name, false, 'input');
  genericForm.sendValueToPrimitive('numCPUs', numCPUs, false, 'input');
  genericForm.sendValueToPrimitive('diskSize', diskSize, false, 'input');
  genericForm.sendValueToPrimitive('memSize', memSize, false, 'input');
  browser.actions().click(element(by.id('new-flavor-generic-form-id')).element(by.binding('GENERIC_FORM.SAVE'))).perform();
  browser.waitForAngular();
  common.dismissAlertIfPresent();
};
module.exports.addNewFlavor = addNewFlavor;

var addNewNetwork = function(name, cidr, isExternal, gateway, ipVersion) {
  goToCloudDetailNetwork();
  browser.element(by.id('clouds-network-add-button')).click();
  genericForm.sendValueToPrimitive('networkName', name, false, 'input');
  genericForm.sendValueToPrimitive('cidr', cidr, false, 'input');
  genericForm.sendValueToPrimitive('isExternal', isExternal, false, 'radio');
  genericForm.sendValueToPrimitive('gatewayIp', gateway, false, 'input');
  genericForm.sendValueToPrimitive('ipVersion', ipVersion, false, 'select');
  browser.actions().click(element(by.id('new-network-generic-form-id')).element(by.binding('GENERIC_FORM.SAVE'))).perform();
  browser.waitForAngular();
  common.dismissAlertIfPresent();
};
module.exports.addNewNetwork = addNewNetwork;

var deleteCoudNetwork = function(name) {
  browser.actions().click(element(by.id('cloud_delete_network_' + name))).perform();
};
module.exports.deleteCoudNetwork = deleteCoudNetwork;

var addNewStorage = function(id, device, size) {
  goToCloudDetailStorage();
  browser.element(by.id('clouds-storage-add-button')).click();
  genericForm.sendValueToPrimitive('id', id, false, 'input');
  genericForm.sendValueToPrimitive('device', device, false, 'input');
  genericForm.sendValueToPrimitive('size', size, false, 'input');
  browser.actions().click(element(by.id('new-storage-generic-form-id')).element(by.binding('GENERIC_FORM.SAVE'))).perform();
  browser.waitForAngular();
  common.dismissAlertIfPresent();
};
module.exports.addNewStorage = addNewStorage;

var deleteCoudStorage = function(name) {
  browser.actions().click(element(by.id('cloud_delete_storage_' + name))).perform();
};
module.exports.deleteCoudStorage = deleteCoudStorage;

var selectFirstImageOfCloud = function() {
  goToCloudDetailImage();
  browser.actions().click(element.all(by.repeater('cloudImage in searchImageData.data')).first().element(by.css('div[ng-click^="switchCloudImageAddSelection"]'))).perform();
  browser.element(by.id('btn-add-cloud-image')).click();
  browser.waitForAngular();
};
module.exports.selectFirstImageOfCloud = selectFirstImageOfCloud;

var selectAllImageOfCloud = function() {
  goToCloudDetailImage();
  element.all(by.repeater('cloudImage in searchImageData.data')).then(function(images) {
    var nb = images.length;
    for (var int = 0; int < nb; int++) {
      browser.actions().click(element.all(by.repeater('cloudImage in searchImageData.data')).first().element(by.css('div[ng-click^="switchCloudImageAddSelection"]'))).perform();
      browser.element(by.id('btn-add-cloud-image')).click();
      browser.waitForAngular();
    }
  });
};
module.exports.selectAllImageOfCloud = selectAllImageOfCloud;

var selectImageOfCloud = function(imageName) {
  goToCloudDetailImage();
  //  browser.actions().click(element.all(by.repeater('cloudImage in data.data')).first().element(by.css('div[ng-click^="selectImage"]'))).perform();
  browser.actions().click(element(by.id('imagesToAdd')).element(by.id('li_' + imageName))).perform();
  browser.element(by.id('btn-add-cloud-image')).click();
  browser.waitForAngular();
};
module.exports.selectImageOfCloud = selectImageOfCloud;

// add a new cloud image from the clouds details images tab
var addNewCloudImage = function(name, osType, osArch, osDistribution, osVersion, numCPUs, diskSize, memSize) {
  goToCloudDetailImage();
  element(by.id('clouds-image-create-button')).click();
  browser.waitForAngular();
  cloudImagesCommon.fillCloudImageCreationForm(name, osType, osArch, osDistribution, osVersion, numCPUs, diskSize, memSize, 'newCloudImageModal');
};
module.exports.addNewCloudImage = addNewCloudImage;

var deleteCoudImage = function(name) {
  browser.actions().click(element(by.id('cloud_delete_cloud_image_' + name))).perform();
};
module.exports.deleteCoudImage = deleteCoudImage;

var assignPaaSResourceToImage = function(cloudImageName, paaSResourceId) {
  goToCloudDetailImage();
  common.sendValueToXEditable('cloudImage_' + cloudImageName, paaSResourceId, false);
};
module.exports.assignPaaSResourceToImage = assignPaaSResourceToImage;

var assignPaaSResourceToFlavor = function(flavorId, paaSResourceId) {
  goToCloudDetailFlavor();
  common.sendValueToXEditable('cloud_flavor_' + flavorId, paaSResourceId, false);
};
module.exports.assignPaaSResourceToFlavor = assignPaaSResourceToFlavor;

var assignPaaSIdToNetwork = function(networkName, paaSResourceId) {
  goToCloudDetailNetwork();
  common.sendValueToXEditable('cloud_network_' + networkName, paaSResourceId, false);
};
module.exports.assignPaaSIdToNetwork = assignPaaSIdToNetwork;

var countAndSelectResourcePaaSIdFromDropDown = function(dropDownButtonId, pasSId, repeaterExpression, elementCount) {
  // click on the button
  browser.actions().click(element(by.id(dropDownButtonId))).perform();
  // count the number of elements in the drop down
  expect(element(by.id(dropDownButtonId)).element(by.xpath('..')).all(by.repeater(repeaterExpression)).count()).toBe(elementCount);
  // select the on we want
  browser.actions().click(element(by.id(dropDownButtonId + '_' + pasSId))).perform();
  // check that the value is as expected
  expect(element(by.id(dropDownButtonId)).getText()).toEqual(pasSId);
};
module.exports.countAndSelectResourcePaaSIdFromDropDown = countAndSelectResourcePaaSIdFromDropDown;

var showMetaProperties = function() {
  element(by.id('toogle-meta-properties')).click();
};
module.exports.showMetaProperties = showMetaProperties;
