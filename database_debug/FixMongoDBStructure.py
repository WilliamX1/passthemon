# 修复数据库base64保存格式问题

import pymongo, logging

# 连接MongoDB数据库
def connectMongoDB():
    myclient = pymongo.MongoClient("mongodb://localhost:27017/")
    mydb = myclient["passthemon"]
    mycol = mydb["Images"]
    return mycol, mydb

mycol, mydb = connectMongoDB()

def changeStructure():
    collist = mydb.list_collection_names()
    if "images-new" in collist:
        mydb["images-new"].drop()
    newcol = mydb["images-new"]
    
    origin_list = mycol.find()
    idx = 1
    for ele in origin_list:
        logging.info("idx" + str(idx))
        idx = idx + 1
        goods_id = int(ele["goods_id"])
        image = ele["image"]
        _class = "com.backend.passthemon.entity.Images"
        origin_data_list = newcol.find({"goods_id": goods_id})
        
        flag = True
        for origin_data in origin_data_list:
            flag = False
            new_images_list = origin_data["images_list"]
            new_images_list.append(image)
            newcol.update_one({"goods_id": goods_id}, {"$set": {"images_list": new_images_list}})
            logging.info("Update one successfully " + str(goods_id))
            break
        if flag:
            newcol.insert_one({"_class": _class, "goods_id": goods_id, "images_list": [image]})
            logging.info("Insert one successfully " + str(goods_id))
   
logging.basicConfig(format = '%(asctime)s - %(pathname)s[line:%(lineno)d]\n%(levelname)s: %(message)s',
                    level = logging.DEBUG)
changeStructure()
        
        