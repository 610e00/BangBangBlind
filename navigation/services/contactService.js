const db = require("./dbService");

/**

* 📌 取得所有聯絡人
  */
  function getAllContacts() {

  const data = db.readDB();

  return data.contacts;
  }

/**

* 📌 根據名字取得聯絡人
  */
  function getContactByName(name) {

  if (!name) return null;

  const contacts = getAllContacts();

  return contacts.find(
  c => c.name.toLowerCase() === name.toLowerCase()
  );
  }

/**

* 📌 第一順位聯絡人
  */
  function getPrimaryContact() {

  const contacts = getAllContacts();

  return [...contacts]
  .sort((a, b) => a.priority - b.priority)[0];
  }

/**

* 📌 相容舊系統
  */
  function getEmergencyContact() {
  return getPrimaryContact();
  }

/**

* 📌 統一解析聯絡人
  */
  function resolveContact(targetName = null) {

  if (targetName) {

  ```
   const contact = getContactByName(targetName);

   if (contact) return contact;
  ```

  }

  return getPrimaryContact();
  }

/**

* 📌 新增聯絡人
  */
  function addContact(name, phone, priority = 99) {

  const data = db.readDB();

  const newContact = {
  id: Date.now(),
  name,
  phone,
  priority
  };

  if (!data.contacts) data.contacts = [];
  data.contacts.push(newContact);

  db.writeDB(data);

  return {
  status: "success",
  message: "contact added",
  data: newContact
  };
  }

/**

* 📌 更新聯絡人
  */
  function updateEmergencyContact(name, phone) {

  const data = db.readDB();

  const contact = data.contacts.find(
  c => c.name.toLowerCase() === name.toLowerCase()
  );

  if (contact) {

  ```
   contact.phone = phone;

   db.writeDB(data);
  ```

  }

  return {
  status: "success",
  message: "contact updated",
  data: data.contacts
  };
  }

/**

* 📌 刪除聯絡人
  */
  function deleteContact(name) {

  const data = db.readDB();

  data.contacts = data.contacts.filter(
  c => c.name.toLowerCase() !== name.toLowerCase()
  );
  if (!data.contacts) data.contacts = [];
  db.writeDB(data);

  return {
  status: "success",
  message: "contact deleted",
  data: data.contacts
  };
  }

module.exports = {
getEmergencyContact,
getAllContacts,
getContactByName,
getPrimaryContact,
resolveContact,
addContact,
updateEmergencyContact,
deleteContact
};
