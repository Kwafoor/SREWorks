/**
 * Created by wangkaihua on 2021/04/26.
 * 组件的元数据信息定义,包括类型、logo、配置Schema等，是组件的描述信息定义
 */
export default {
  "id": "StaticCard",
  "type": "StaticCard",
  "name": "StaticCard",
  "title": "统计卡片",
  "info": {
    "author": {
      "name": "",
      "url": "",
    },
    "description": "一种常见的数据统计卡片",
    "links": [],
    "logos": {
      "large": "",
      "small": require("./icon.svg"),
    },
    "build": {
      "time": "",
      "repo": "",
      "branch": "",
      "hash": "",
    },
    "screenshots": [],
    "updated": "",
    "version": "",
    "docs": "<a target='_blank' href='https://3x.ant.design/components/list-cn/'>组件文档地址</a>",
  },
  "state": "",
  "latestVersion": "1.0",
  "configSchema": {
    "defaults": {
      "type": "List",
      "config": {
        "flex": 6,
        "bordered": true,
        "listItem": {
          "avatar": "avatar",
          "title": "title",
          "auth": "auth",
          "date": "date",
          "description": "description",
        },
      },
    },
    "schema": {
      "type": "object",
      "properties": {
        "cardTitle": {
          "description": "卡片标题",
          "title": "卡片标题",
          "required": true,
          "x-component": "Input",
          "type": "string",
        },
        "backgroundImg": {
          "description": "标题icon",
          "title": "标题icon",
          "required": false,
          "type": "string",
          "x-component": "ImageUpload",
          "initValue": "",
        },
        "bordered": {
          "description": "卡片边框",
          "title": "卡片边框",
          "required": false,
            "type": "string",
            "initValue":true,
            "x-component": "Radio",
            "x-component-props": {
              "options": [{"value": false, "label": "否"}, {"value": true, "label": "是"}],
            },
        },
        "cardIcon": {
          "description": "卡片icon，效果同本地上传图片，用于配置卡片头部icon，优先级低于本地上传图片",
          "title": "卡片icon",
          "required": false,
          "type": "string",
          "x-component": "Input",
          "type": "string",
        },
        "cardWidth": {
          "description": "卡片宽度",
          "title": "卡片宽度",
          "required": false,
          "type": "string",
          "x-component": "Input",
          "type": "string",
        },
        "cardHeight": {
          "description": "卡片高度",
          "title": "卡片高度",
          "required": false,
          "type": "string",
          "x-component": "Input",
          "type": "string",
        },
        "renderAction": {
          "description": "卡片交互操作区域，在此为render",
          "title": "操作",
          "required": false,
          "type": "string",
          "x-component": "Text",
          "type": "string",
        },
      },
    },
    "supportItemToolbar": false,
    "dataMock": {},
  },
  "catgory": "base",
};