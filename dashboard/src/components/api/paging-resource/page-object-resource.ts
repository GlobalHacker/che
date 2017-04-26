/*
 * Copyright (c) 2015-2017 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 */
'use strict';

import {RemotePageLabels} from './remote-page-labels';

interface ITransformResponse {
  objects: Array<any>;
  links?: Map<string, string>;
}

interface IPageData {
  objects?: Array<any>;
  link: string;
}

interface IPageParam {
  maxItems: number;
  skipCount: number;
}

const LINK = 'link';
const MAX_ITEMS = 'maxItems';
const SKIP_COUNT = 'skipCount';

interface IPageDataResource<T> extends ng.resource.IResourceClass<T> {
  getPageData(): ng.resource.IResource<T>;
}

/**
 * A helper class to simplify getting paging resource.
 * @author Oleksii Orel
 */
export class PageObjectResource {

  private $q: ng.IQService;
  private $resource: ng.resource.IResourceService;
  private remoteDataAPI: IPageDataResource<any>;

  private pagesInfo: che.IPageInfo;
  private data: che.IRequestData;
  private pageObjects: Array<any> = [];
  private objectPagesMap: Map<number, IPageData> = new Map();
  private objectKey: string;
  private objectMap: Map<string, any>;

  constructor(url: string, data: che.IRequestData, $q: ng.IQService, $resource: ng.resource.IResourceService, objectKey?: string, objectMap?: Map<string, any>) {
    this.$q = $q;
    this.data = data;
    this.$resource = $resource;
    this.objectKey = objectKey;
    this.objectMap = objectMap;

    // remote call
    this.remoteDataAPI = <IPageDataResource<any>> this.$resource(url, this.data, {
      getPageData: {
        method: 'GET',
        isArray: false,
        responseType: 'json',
        transformResponse: (data: Array<any>, headersGetter: Function) => {
          return this._getPageFromResponse(data, headersGetter(LINK));
        }
      }
    });

    // set default values
    this.pagesInfo = {countPages: 0, currentPageNumber: 1};
  }

  _getPageFromResponse(data: Array<any>, headersLink: string): ITransformResponse {
    let newData = [];
    if (angular.isDefined(data) && angular.isArray(data)) {
      data.forEach((object: any) => {
        // add an object
        if (this.objectKey) {
          let val = object[this.objectKey];
          newData.push(val);
          if (this.objectMap && !angular.equals(object, this.objectMap.get(val))) {
            this.objectMap.set(object[this.objectKey], object);
          }
        } else {
          newData.push(object);
        }
      });
    }
    let links: Map<string, string> = new Map();
    if (!headersLink) {
      return {objects: newData};
    }
    let pattern = new RegExp('<([^>]+?)>.+?rel="([^"]+?)"', 'g');
    let result;
    // look for pattern
    while (result = pattern.exec(headersLink)) {
      // add link
      links.set(result[2], result[1]);
    }
    return {
      objects: newData,
      links: links
    };
  }

  _getPageParamByLink(pageLink: string): IPageParam {
    let lastPageParamMap = new Map();
    let pattern = new RegExp('([_\\w]+)=([\\w]+)', 'g');
    let result;
    while (result = pattern.exec(pageLink)) {
      lastPageParamMap.set(result[1], result[2]);
    }
    let skipCount = lastPageParamMap.get(SKIP_COUNT);
    let maxItems = lastPageParamMap.get(MAX_ITEMS);

    return {
      maxItems: maxItems ? maxItems : 0,
      skipCount: skipCount ? skipCount : 0
    };
  }

  _updateCurrentPageData(data?: ITransformResponse): void {
    let pageData: IPageData = this.objectPagesMap.get(this.pagesInfo.currentPageNumber);
    if (angular.isDefined(data) && angular.isDefined(pageData)) {
      pageData.objects = data.objects;
    }
    // update objects for current page
    this.pageObjects.length = 0;
    if (angular.isUndefined(pageData) || !angular.isArray(pageData.objects) || pageData.objects.length === 0) {
      return;
    }
    pageData.objects.forEach((object: any) => {
      this.pageObjects.push(object);
    });
  }

  /**
   * Update page links by relative direction ('first', 'prev', 'next', 'last')
   *
   * @param data {ITransformResponse}
   */
  _updatePageLinks(data: ITransformResponse): void {
    if (!data || !data.links) {
      return;
    }
    // first page link
    this._updatePagesMapLinks(1, data.links.get(RemotePageLabels.FIRST));
    // last page link
    let lastPageLink = data.links.get(RemotePageLabels.LAST);
    let pageParam = this._getPageParamByLink(lastPageLink);
    let countPages = pageParam.skipCount / pageParam.maxItems + 1;
    this.pagesInfo.countPages = countPages;
    this._updatePagesMapLinks(countPages, lastPageLink);
    // previous page link
    let prevPageNumber = this.pagesInfo.currentPageNumber - 1;
    this._updatePagesMapLinks(prevPageNumber, data.links.get(RemotePageLabels.PREVIOUS));
    // next page link
    let nextPageNumber = this.pagesInfo.currentPageNumber + 1;
    this._updatePagesMapLinks(nextPageNumber, data.links.get(RemotePageLabels.NEXT));
  }

  _updatePagesMapLinks(pageNumber: number, pageLink: string): void {
    if (!pageNumber || !pageLink) {
      return;
    }
    let pageData: IPageData;
    if (this.objectPagesMap.has(pageNumber)) {
      pageData = this.objectPagesMap.get(pageNumber);
      pageData.link = pageLink;
    } else {
      pageData = {link: pageLink};
      this.objectPagesMap.set(pageNumber, pageData);
    }
  }

  /**
   * Ask for loading the objects in asynchronous way
   * If there are no changes, it's not updated
   * @param maxItems - the max number of items to return
   * @returns {*} the promise
   */
  fetchObjects(maxItems?: number): ng.IPromise<any> {
    let skipCount = 0;
    this.pagesInfo.currentPageNumber = 1;
    if (maxItems) {
      this.data.maxItems = maxItems.toString();
    }
    this.data.skipCount = skipCount.toString();
    let promise = this.remoteDataAPI.getPageData().$promise;

    return promise.then((data: ITransformResponse) => {
      this._updatePageLinks(data);
      this._updateCurrentPageData(data);
      return this.$q.when(this.getPageObjects());
    }, (error: any) => {
      if (error && error.status === 304) {
        return this.$q.when(this.getPageObjects());
      }
      return this.$q.reject(error);
    });
  }

  /**
   * Ask for loading any page objects depends on page key ('first', 'prev', 'next', 'last'  or '1', '2', '3' ...)
   * @param pageKey {string} - the key of page
   * @returns {ng.IPromise<Array<any>>} the promise
   */
  fetchPageObjects(pageKey: string): ng.IPromise<Array<any>> {
    let deferred = this.$q.defer();
    let pageNumber;
    switch (pageKey) {
      case RemotePageLabels.FIRST:
        pageNumber = 1;
        break;
      case RemotePageLabels.PREVIOUS:
        pageNumber = this.pagesInfo.currentPageNumber - 1;
        break;
      case RemotePageLabels.NEXT:
        pageNumber = this.pagesInfo.currentPageNumber + 1;
        break;
      case RemotePageLabels.LAST:
        pageNumber = this.pagesInfo.countPages;
        break;
      default:
        pageNumber = parseInt(pageKey, 10);
    }
    if (isNaN(pageNumber) || pageNumber < 1) {
      deferred.reject({data: {message: 'Error. Invalid page key.'}});
      return deferred.promise;
    }
    let pageData = this.objectPagesMap.get(pageNumber);
    if (pageData && pageData.link) {
      this.pagesInfo.currentPageNumber = pageNumber;

      let pageParam = this._getPageParamByLink(pageData.link);
      this.data.maxItems = pageParam.maxItems.toString();
      this.data.skipCount = pageParam.skipCount.toString();

      let promise = this.remoteDataAPI.getPageData().$promise;
      promise.then((data: ITransformResponse) => {
        this._updatePageLinks(data);
        this._updateCurrentPageData(data);
        deferred.resolve(this.getPageObjects());
      }, (error: any) => {
        if (error && error.status === 304) {
          this._updateCurrentPageData();
          deferred.resolve(this.getPageObjects());
        }
        deferred.reject(error);
      });
    } else {
      deferred.reject({data: {message: 'Error. No necessary link.'}});
    }

    return deferred.promise;
  }

  /**
   * Gets the pageInfo object
   * @returns {IPageInfo}
   */
  getPagesInfo(): che.IPageInfo {
    return this.pagesInfo;
  }

  /**
   * Gets the page objects
   * @returns {Array<any>}
   */
  getPageObjects(): Array<any> {
    if (angular.isUndefined(this.objectKey) || angular.isUndefined(this.objectMap)) {
      return this.pageObjects;
    }
    let newPageObjects = [];
    this.pageObjects.forEach((objectKey: string) => {
      // add an object
      newPageObjects.push(this.objectMap.get(objectKey));
    });

    return newPageObjects;
  }

  /**
   * Gets the request data object
   * @returns {che.IRequestData}
   */
  getRequestDataObject(): che.IRequestData {
    return this.data;
  }

  /**
   * Gets the object key
   * @returns {string}
   */
  getObjectKey(): string {
    return this.objectKey;
  }
}
